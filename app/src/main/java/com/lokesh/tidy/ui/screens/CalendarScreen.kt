package com.lokesh.tidy.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Subtask
import com.lokesh.tidy.data.Task
import com.lokesh.tidy.data.TaskWithSubtasks
import com.lokesh.tidy.ui.Dates
import com.lokesh.tidy.ui.components.*
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    tasks: List<TaskWithSubtasks>,
    categories: List<Category>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onToggle: (Task) -> Unit,
    onOpen: (TaskWithSubtasks) -> Unit,
    onDelete: (TaskWithSubtasks) -> Unit,
    onSubtask: (Subtask) -> Unit,
    onReorder: (List<Task>) -> Unit,
) {
    val t = tidy
    val haptic = LocalHapticFeedback.current
    var monthView by rememberSaveable { mutableStateOf(false) }
    val catMap = remember(categories) { categories.associateBy { it.id } }
    val byDay = remember(tasks) { tasks.filter { it.task.dueDate != null }.groupBy { it.task.dueDate!! } }
    val dayTasks = remember(byDay, selected) {
        (byDay[selected.toEpochDay()] ?: emptyList()).sortedWith(
            compareBy<TaskWithSubtasks> { it.task.completed }
                .thenBy { it.task.dueTime ?: -1 }
                .thenBy { it.task.sortOrder }
                .thenByDescending { it.task.completedAt ?: 0L }
        )
    }
    val computedAllDay = remember(dayTasks) { dayTasks.filter { it.task.dueTime == null } }
    val computedTimed = remember(dayTasks) { dayTasks.filter { it.task.dueTime != null } }

    var allDay by remember { mutableStateOf(computedAllDay) }
    var timed by remember { mutableStateOf(computedTimed) }
    var activeGroup by remember { mutableStateOf<String?>(null) }
    var activeTime by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(computedAllDay, computedTimed) {
        if (activeGroup == null) {
            allDay = computedAllDay
            timed = computedTimed
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fk = from.key as? String ?: return@rememberReorderableLazyListState
        val tk = to.key as? String ?: return@rememberReorderableLazyListState
        if (fk.startsWith("a_") && tk.startsWith("a_")) {
            val fi = allDay.indexOfFirst { "a_${it.task.id}" == fk }
            val ti = allDay.indexOfFirst { "a_${it.task.id}" == tk }
            if (fi >= 0 && ti >= 0 && !allDay[fi].task.completed && !allDay[ti].task.completed) {
                allDay = allDay.toMutableList().apply { add(ti, removeAt(fi)) }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else if (fk.startsWith("t_") && tk.startsWith("t_")) {
            val fi = timed.indexOfFirst { "t_${it.task.id}" == fk }
            val ti = timed.indexOfFirst { "t_${it.task.id}" == tk }
            if (fi >= 0 && ti >= 0 && !timed[fi].task.completed && !timed[ti].task.completed && timed[fi].task.dueTime == timed[ti].task.dueTime) {
                timed = timed.toMutableList().apply { add(ti, removeAt(fi)) }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 140.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selected.year.toString(), style = MaterialTheme.typography.labelLarge, color = t.textMuted)
                    Text(selected.month.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.headlineLarge, color = t.text)
                }
                IconBubble(Icons.Rounded.ChevronLeft, { onSelect(if (monthView) selected.minusMonths(1) else selected.minusWeeks(1)) })
                Spacer(Modifier.width(8.dp))
                IconBubble(Icons.Rounded.ChevronRight, { onSelect(if (monthView) selected.plusMonths(1) else selected.plusWeeks(1)) })
            }
        }
        item(key = "calendar_card") {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(t.card).padding(12.dp)
                    .pointerInput(monthView, selected) {
                        var total = 0f
                        detectHorizontalDragGestures(onDragEnd = {
                            if (total > 80) onSelect(if (monthView) selected.minusMonths(1) else selected.minusWeeks(1))
                            else if (total < -80) onSelect(if (monthView) selected.plusMonths(1) else selected.plusWeeks(1))
                            total = 0f
                        }) { _, d -> total += d }
                    }
                    .animateContentSize()
            ) {
                Row(Modifier.fillMaxWidth()) {
                    DayOfWeek.entries.forEach {
                        Text(it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, color = t.textFaint)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val weeks: List<List<LocalDate>> = if (monthView) {
                    val ym = YearMonth.from(selected)
                    val first = ym.atDay(1).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
                    val last = ym.atEndOfMonth().let { it.plusDays((7 - it.dayOfWeek.value).toLong()) }
                    generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList().chunked(7)
                } else {
                    val start = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
                    listOf((0..6).map { start.plusDays(it.toLong()) })
                }
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { d ->
                            DayCell(d, d == selected, d == LocalDate.now(), !monthView || d.month == selected.month,
                                byDay[d.toEpochDay()]?.let { l -> l.count { !it.task.completed } to l.size } ?: (0 to 0),
                                Modifier.weight(1f)) { onSelect(d) }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    Icon(if (monthView) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, "Toggle month", tint = t.textFaint,
                        modifier = Modifier.clip(CircleShape).clickable { monthView = !monthView }.padding(horizontal = 24.dp, vertical = 2.dp))
                }
            }
        }
        item(key = "day_title") {
            Row(Modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected == LocalDate.now()) "Today" else Dates.short(selected), style = MaterialTheme.typography.titleLarge, color = t.text)
                Spacer(Modifier.width(10.dp))
                if (dayTasks.isNotEmpty()) MetaChip("${dayTasks.count { it.task.completed }}/${dayTasks.size} done", bg = t.accentSoft, tint = t.accentStrong)
            }
        }
        if (dayTasks.isEmpty()) {
            item(key = "empty") { EmptyState("🗓️", "A free day", "Tap + to plan something here") }
        }
        if (allDay.isNotEmpty()) {
            item(key = "h_allday") { SectionHeader("All day", allDay.size) }
            items(allDay, key = { "a_${it.task.id}" }) { item ->
                if (item.task.completed) {
                    SwipeableTaskCard(
                        item = item,
                        category = item.task.categoryId?.let(catMap::get),
                        showDate = false,
                        isDragging = false,
                        onToggle = { onToggle(item.task) },
                        onOpen = { onOpen(item) },
                        onDelete = { onDelete(item) },
                        onSubtask = onSubtask,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    val canTarget = activeGroup == null || activeGroup == "allday"
                    ReorderableItem(reorderState, key = "a_${item.task.id}", enabled = canTarget) { isDragging ->
                        SwipeableTaskCard(
                            item = item,
                            category = item.task.categoryId?.let(catMap::get),
                            showDate = false,
                            isDragging = isDragging,
                            onToggle = { onToggle(item.task) },
                            onOpen = { onOpen(item) },
                            onDelete = { onDelete(item) },
                            onSubtask = onSubtask,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        activeGroup = "allday"
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        activeGroup = null
                                        onReorder(allDay.filter { !it.task.completed }.map { it.task })
                                    }
                                )
                        )
                    }
                }
            }
        }
        if (timed.isNotEmpty()) {
            item(key = "h_timeline") { SectionHeader("Timeline", timed.size) }
            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            val isToday = selected == LocalDate.now()
            items(timed, key = { "t_${it.task.id}" }) { item ->
                val past = isToday && (item.task.dueTime ?: 0) < nowMin
                val canTarget = !item.task.completed && (activeGroup == null || (activeGroup == "timed" && activeTime == item.task.dueTime))
                ReorderableItem(reorderState, key = "t_${item.task.id}", enabled = canTarget) { isDragging ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .then(
                                if (item.task.completed) Modifier
                                else Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        activeGroup = "timed"
                                        activeTime = item.task.dueTime
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        activeGroup = null
                                        activeTime = null
                                        onReorder(timed.filter { !it.task.completed }.map { it.task })
                                    }
                                )
                            )
                    ) {
                        Column(Modifier.width(62.dp).padding(top = 14.dp), horizontalAlignment = Alignment.Start) {
                            val (time, ampm) = Dates.time(item.task.dueTime!!).split(" ").let { it[0] to it.getOrElse(1) { "" } }
                            Text(time, style = MaterialTheme.typography.titleSmall, color = if (past) t.textFaint else t.text)
                            Text(ampm, style = MaterialTheme.typography.labelSmall, color = t.textFaint)
                        }
                        Box(Modifier.width(20.dp).fillMaxHeight()) {
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(10.dp).clip(CircleShape)
                                .background(if (item.task.completed) t.textFaint else Palette.priority[item.task.priority].takeIf { item.task.priority > 0 } ?: t.accentStrong))
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 32.dp).width(2.dp).fillMaxHeight().background(t.divider))
                        }
                        Spacer(Modifier.width(6.dp))
                        SwipeableTaskCard(
                            item = item,
                            category = item.task.categoryId?.let(catMap::get),
                            showDate = false,
                            isDragging = isDragging,
                            onToggle = { onToggle(item.task) },
                            onOpen = { onOpen(item) },
                            onDelete = { onDelete(item) },
                            onSubtask = onSubtask,
                            modifier = Modifier.weight(1f).padding(bottom = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, selected: Boolean, today: Boolean, inMonth: Boolean, counts: Pair<Int, Int>, modifier: Modifier, onClick: () -> Unit) {
    val t = tidy
    val (open, total) = counts
    Column(
        modifier.padding(3.dp).clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    selected -> Brush.verticalGradient(listOf(t.accentStrong, t.accentStrong.copy(alpha = 0.8f)))
                    today -> Brush.verticalGradient(listOf(t.accentSoft, t.accentSoft))
                    else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            d.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected || today) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = when { selected -> if (t.dark) Color(0xFF121214) else Color.White; !inMonth -> t.textFaint.copy(alpha = 0.5f); else -> t.text }
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(5.dp)) {
            val dots = minOf(total, 3)
            repeat(dots) { i ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(
                    if (selected) (if (t.dark) Color(0xFF121214) else Color.White).copy(alpha = if (i < open) 1f else 0.4f)
                    else if (i < open) t.accentStrong else t.textFaint
                ))
            }
        }
    }
}
