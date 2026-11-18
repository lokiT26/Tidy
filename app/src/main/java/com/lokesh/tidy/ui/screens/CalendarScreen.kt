package com.lokesh.tidy.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    tasks: List<TaskWithSubtasks>,
    categories: List<Category>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onToggle: (Task) -> Unit,
    onOpen: (TaskWithSubtasks) -> Unit,
    onSubtask: (Subtask) -> Unit,
) {
    val t = tidy
    var monthView by rememberSaveable { mutableStateOf(false) }
    val catMap = remember(categories) { categories.associateBy { it.id } }
    val byDay = remember(tasks) { tasks.filter { it.task.dueDate != null }.groupBy { it.task.dueDate!! } }
    val dayTasks = (byDay[selected.toEpochDay()] ?: emptyList()).sortedWith(compareBy({ it.task.completed }, { it.task.dueTime ?: -1 }, { it.task.sortOrder }))
    val allDay = dayTasks.filter { it.task.dueTime == null }
    val timed = dayTasks.filter { it.task.dueTime != null }.sortedBy { it.task.dueTime }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 140.dp)) {
        item {
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
        item {
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
        item {
            Row(Modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected == LocalDate.now()) "Today" else Dates.short(selected), style = MaterialTheme.typography.titleLarge, color = t.text)
                Spacer(Modifier.width(10.dp))
                if (dayTasks.isNotEmpty()) MetaChip("${dayTasks.count { it.task.completed }}/${dayTasks.size} done", bg = t.accentSoft, tint = t.accentStrong)
            }
        }
        if (dayTasks.isEmpty()) {
            item { EmptyState("🗓️", "A free day", "Tap + to plan something here") }
        }
        if (allDay.isNotEmpty()) {
            item { SectionHeader("All day", allDay.size) }
            items(allDay, key = { "a" + it.task.id }) {
                TaskCard(it, it.task.categoryId?.let(catMap::get), { onToggle(it.task) }, { onOpen(it) }, onSubtask, Modifier.padding(bottom = 8.dp).animateItem(), showDate = false)
            }
        }
        if (timed.isNotEmpty()) {
            item { SectionHeader("Timeline", timed.size) }
            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            val isToday = selected == LocalDate.now()
            items(timed, key = { "t" + it.task.id }) { item ->
                val past = isToday && (item.task.dueTime ?: 0) < nowMin
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).animateItem()) {
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
                    TaskCard(item, item.task.categoryId?.let(catMap::get), { onToggle(item.task) }, { onOpen(item) }, onSubtask,
                        Modifier.weight(1f).padding(bottom = 10.dp), showDate = false)
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
