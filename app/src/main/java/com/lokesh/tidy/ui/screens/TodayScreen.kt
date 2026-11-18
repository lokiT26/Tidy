package com.lokesh.tidy.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Task
import com.lokesh.tidy.data.TaskWithSubtasks
import com.lokesh.tidy.data.Repository
import com.lokesh.tidy.ui.Dates
import com.lokesh.tidy.ui.components.*
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private sealed interface Row { val key: String }
private data class HeaderRow(val section: String, val title: String, val count: Int, val color: Color? = null) : Row { override val key = "h_$section" }
private data class TaskRow(val section: String, val item: TaskWithSubtasks) : Row { override val key = "t_${item.task.id}" }

fun isToday(ms: Long?): Boolean = ms != null && Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    name: String,
    tasks: List<TaskWithSubtasks>,
    categories: List<Category>,
    confettiEnabled: Boolean,
    onToggle: (Task) -> Unit,
    onOpen: (TaskWithSubtasks) -> Unit,
    onDelete: (TaskWithSubtasks) -> Unit,
    onSubtask: (com.lokesh.tidy.data.Subtask) -> Unit,
    onReorder: (List<Task>) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    selectedCategory: Long?,
    onSelectCategory: (Long?) -> Unit,
    onAddCategory: () -> Unit,
) {
    val t = tidy
    val haptic = LocalHapticFeedback.current
    val today = Dates.today()
    val catMap = remember(categories) { categories.associateBy { it.id } }
    var showCompleted by rememberSaveable { mutableStateOf(false) }

    val filtered = remember(tasks, selectedCategory) { if (selectedCategory == null) tasks else tasks.filter { it.task.categoryId == selectedCategory } }

    // Progress for today
    val todayPool = filtered.filter { val d = it.task.dueDate; d != null && (d == today || (d < today && (!it.task.completed || isToday(it.task.completedAt)))) }
    val doneCount = todayPool.count { it.task.completed }
    val total = todayPool.size
    val streak = remember(tasks) { Repository.streaks(tasks.map { it.task }).first }

    var confetti by remember { mutableIntStateOf(0) }
    var lastRatio by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(doneCount, total) {
        val r = if (total == 0) 0f else doneCount / total.toFloat()
        if (confettiEnabled && lastRatio != null && lastRatio!! < 1f && r >= 1f && total > 0) confetti++
        lastRatio = r
    }

    val computed: List<Row> = remember(filtered, showCompleted) {
        val open = filtered.filter { !it.task.completed }
        val overdue = open.filter { it.task.dueDate != null && it.task.dueDate < today }.sortedWith(compareBy({ it.task.dueDate }, { it.task.sortOrder }))
        val todays = open.filter { it.task.dueDate == today }.sortedWith(compareBy({ it.task.sortOrder }))
        val upcoming = open.filter { it.task.dueDate != null && it.task.dueDate > today }.sortedWith(compareBy({ it.task.dueDate }, { it.task.sortOrder }))
        val someday = open.filter { it.task.dueDate == null }.sortedBy { it.task.sortOrder }
        val done = filtered.filter { it.task.completed }.sortedByDescending { it.task.completedAt ?: 0 }.take(40)
        buildList {
            if (overdue.isNotEmpty()) { add(HeaderRow("overdue", "Overdue", overdue.size, Palette.danger)); overdue.forEach { add(TaskRow("overdue", it)) } }
            add(HeaderRow("today", "Today", todays.size)); todays.forEach { add(TaskRow("today", it)) }
            if (upcoming.isNotEmpty()) { add(HeaderRow("upcoming", "Upcoming", upcoming.size)); upcoming.forEach { add(TaskRow("upcoming", it)) } }
            if (someday.isNotEmpty()) { add(HeaderRow("someday", "Someday", someday.size)); someday.forEach { add(TaskRow("someday", it)) } }
            if (done.isNotEmpty()) { add(HeaderRow("done", "Completed", done.size)); if (showCompleted) done.forEach { add(TaskRow("done", it)) } }
        }
    }
    var rows by remember { mutableStateOf(computed) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(computed) { if (!dragging) rows = computed }

    val listState = rememberLazyListState()
    val headerItems = 3 // greeting, progress, chips
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = rows.indexOfFirst { it.key == from.key }
        val toIdx = rows.indexOfFirst { it.key == to.key }
        if (fromIdx < 0 || toIdx < 0) return@rememberReorderableLazyListState
        val a = rows[fromIdx]; val b = rows[toIdx]
        if (a is TaskRow && b is TaskRow && a.section == b.section && a.section != "done") {
            rows = rows.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "greeting") { Greeting(name, onSearch, onSettings) }
            item(key = "progress") { ProgressCard(doneCount, total, streak) }
            item(key = "chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    item { SelectChip("All", selectedCategory == null) { onSelectCategory(null) } }
                    items(categories, key = { it.id }) { c ->
                        SelectChip(c.name, selectedCategory == c.id, Palette.vivid.getOrElse(c.colorIndex) { Palette.vivid[0] }, leading = c.emoji.ifBlank { null }) { onSelectCategory(c.id) }
                    }
                    item {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(t.card).border(1.dp, t.divider, CircleShape).clickable(onClick = onAddCategory), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, "Add list", tint = t.textMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is HeaderRow -> {
                        if (row.section == "done") {
                            SectionHeader(row.title, row.count) {
                                Text(if (showCompleted) "Hide" else "Show", style = MaterialTheme.typography.labelMedium, color = t.accentStrong,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showCompleted = !showCompleted }.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        } else if (row.section == "today" && row.count == 0) {
                            Column(Modifier.animateItem()) {
                                SectionHeader(row.title, 0)
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.card).padding(22.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (total > 0) "🎉" else "☀️", fontSize = 30.sp)
                                        Spacer(Modifier.height(6.dp))
                                        Text(if (total > 0) "All clear for today!" else "Nothing planned today", style = MaterialTheme.typography.titleMedium, color = t.text)
                                        Text("Tap + to add something", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                                    }
                                }
                            }
                        } else SectionHeader(row.title, row.count, row.color ?: t.textMuted)
                    }
                    is TaskRow -> {
                        ReorderableItem(reorderState, key = row.key) { isDragging ->
                            SwipeableTask(
                                row = row, category = row.item.task.categoryId?.let { catMap[it] }, today = today, isDragging = isDragging,
                                onToggle = { onToggle(row.item.task) }, onOpen = { onOpen(row.item) }, onDelete = { onDelete(row.item) }, onSubtask = onSubtask,
                                dragModifier = Modifier.longPressDraggableHandle(
                                    onDragStarted = { dragging = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = {
                                        dragging = false
                                        val sectionTasks = rows.filterIsInstance<TaskRow>().filter { it.section == row.section }.map { it.item.task }
                                        onReorder(sectionTasks)
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
        Confetti(confetti)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTask(
    row: TaskRow, category: Category?, today: Long, isDragging: Boolean,
    onToggle: () -> Unit, onOpen: () -> Unit, onDelete: () -> Unit, onSubtask: (com.lokesh.tidy.data.Subtask) -> Unit,
    dragModifier: Modifier,
) {
    val t = tidy
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                else -> false
            }
        },
        positionalThreshold = { it * 0.35f }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            val color = when (dir) { SwipeToDismissBoxValue.StartToEnd -> Palette.success; SwipeToDismissBoxValue.EndToStart -> Palette.danger; else -> Color.Transparent }
            val progress = state.progress
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(color.copy(alpha = 0.18f + 0.5f * progress.coerceIn(0f, 1f))).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    if (dir == SwipeToDismissBoxValue.StartToEnd) (if (row.item.task.completed) Icons.Rounded.Undo else Icons.Rounded.Check) else Icons.Rounded.DeleteOutline,
                    null, tint = Color.White, modifier = Modifier.scale(0.8f + 0.5f * progress.coerceIn(0f, 1f))
                )
            }
        },
        modifier = dragModifier
    ) {
        TaskCard(
            item = row.item, category = category,
            onToggle = onToggle, onClick = onOpen, onSubtaskToggle = onSubtask,
            showDate = row.section != "today", dragging = isDragging
        )
    }
}

@Composable
private fun Greeting(name: String, onSearch: () -> Unit, onSettings: () -> Unit) {
    val t = tidy
    val h = LocalTime.now().hour
    val greet = when { h < 5 -> "Good night"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; h < 21 -> "Good evening"; else -> "Good night" }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(Dates.long(LocalDate.now()), style = MaterialTheme.typography.labelLarge, color = t.textMuted)
            Spacer(Modifier.height(4.dp))
            Text(if (name.isBlank()) "$greet ✨" else "$greet,\n$name", style = MaterialTheme.typography.headlineLarge, color = t.text)
        }
        IconBubble(Icons.Rounded.Search, onSearch)
        Spacer(Modifier.width(10.dp))
        IconBubble(Icons.Rounded.Tune, onSettings)
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, streak: Int) {
    val t = tidy
    val ratio = if (total == 0) 0f else done / total.toFloat()
    val a = t.accent
    val msg = when {
        total == 0 -> "A fresh, open day"
        ratio >= 1f -> "Everything done. Brilliant!"
        ratio >= 0.5f -> "Over halfway — keep going"
        done > 0 -> "Nice start, momentum!"
        else -> "Let's get the first one done"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(if (t.dark) listOf(Color(0xFF2A2440), Color(0xFF3A2A2E)) else listOf(a.pastel, a.partner.copy(alpha = 0.75f))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = if (t.dark) 0.04f else 0.25f), radius = size.height * 0.9f, center = Offset(size.width * 1.05f, -size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = if (t.dark) 0.03f else 0.18f), radius = size.height * 0.5f, center = Offset(size.width * 0.75f, size.height * 1.1f))
            }
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                ProgressRing(ratio, Modifier.fillMaxSize(), 11.dp, track = Color.White.copy(alpha = if (t.dark) 0.08f else 0.45f),
                    colors = if (t.dark) listOf(a.pastel, a.partner) else listOf(a.strong, a.strong.copy(alpha = 0.75f)))
                Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = if (t.dark) t.text else Color(0xFF1B1530))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(if (total == 0) "No tasks today" else "$done of $total done", style = MaterialTheme.typography.titleLarge, color = if (t.dark) t.text else Color(0xFF1B1530))
                Spacer(Modifier.height(2.dp))
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = if (t.dark) t.textMuted else Color(0xFF1B1530).copy(alpha = 0.65f))
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (t.dark) 0.08f else 0.55f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(if (streak == 1) "1 day streak" else "$streak day streak", style = MaterialTheme.typography.labelMedium, color = if (t.dark) t.text else Color(0xFF1B1530))
                }
            }
        }
    }
}
