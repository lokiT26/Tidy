package com.lokesh.tidy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Subtask
import com.lokesh.tidy.data.Task
import com.lokesh.tidy.data.TaskWithSubtasks
import com.lokesh.tidy.ui.Dates
import com.lokesh.tidy.ui.components.ConfirmDeleteButton
import com.lokesh.tidy.ui.components.SelectChip
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private const val MAX_TITLE = 150
private const val MAX_NOTES = 2000
private const val MAX_SUBTASK = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    initial: TaskWithSubtasks?,
    categories: List<Category>,
    defaultDate: Long?,
    defaultCategory: Long?,
    defaultPriority: Int = 0,
    onSave: (Task, List<Subtask>) -> Unit,
    onDelete: ((Task) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val t = tidy
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val validDefaultCat = defaultCategory?.takeIf { id -> categories.any { it.id == id } }
    val base = initial?.task ?: Task(
        title = "",
        dueDate = defaultDate,
        categoryId = validDefaultCat,
        priority = defaultPriority.coerceIn(0, 3),
    )
    var title by remember { mutableStateOf(base.title.take(MAX_TITLE)) }
    var notes by remember { mutableStateOf(base.notes.take(MAX_NOTES)) }
    var dueDate by remember { mutableStateOf(base.dueDate) }
    var dueTime by remember { mutableStateOf(base.dueTime) }
    var reminder by remember { mutableStateOf(base.reminderOffset) }
    var priority by remember { mutableIntStateOf(base.priority) }
    var categoryId by remember { mutableStateOf(base.categoryId) }
    var repeat by remember { mutableStateOf(base.repeat) }
    val subs = remember { mutableStateListOf<Subtask>().apply { addAll(initial?.subtasks?.sortedBy { it.sortOrder } ?: emptyList()) } }
    var newSub by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showCustomReminder by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(base.notes.isNotBlank()) }
    val focus = remember { FocusRequester() }

    fun ensureFutureDefaultTimeIfToday() {
        val now = LocalTime.now()
        val nowMin = now.hour * 60 + now.minute
        if (dueDate == Dates.today() && dueTime == null && nowMin >= 9 * 60) {
            dueTime = ((now.hour + 1).coerceAtMost(23)) * 60
        }
    }

    fun save() {
        if (title.isBlank()) { onDismiss(); return }
        val cleanedSubs = subs.map { it.copy(title = it.title.trim().take(MAX_SUBTASK)) }.filter { it.title.isNotEmpty() }
        val finalSubs = if (newSub.isNotBlank()) cleanedSubs + Subtask(taskId = 0, title = newSub.trim().take(MAX_SUBTASK)) else cleanedSubs
        onSave(base.copy(title = title.trim().take(MAX_TITLE), notes = notes.trim().take(MAX_NOTES), dueDate = dueDate, dueTime = if (dueDate == null) null else dueTime,
            reminderOffset = if (dueDate == null) null else reminder, priority = priority, categoryId = categoryId, repeat = if (dueDate == null) "NONE" else repeat), finalSubs)
    }

    LaunchedEffect(Unit) { if (initial == null) focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = t.card,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(40.dp, 5.dp).clip(CircleShape).background(t.divider)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial == null) "New task" else "Edit task", style = MaterialTheme.typography.labelLarge, color = t.textMuted, modifier = Modifier.weight(1f))
                if (initial != null && onDelete != null) {
                    ConfirmDeleteButton(onConfirmDelete = { onDelete(base) })
                }
            }
            BasicTextField(
                value = title, onValueChange = { if (it.length <= MAX_TITLE) title = it },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = t.text),
                cursorBrush = SolidColor(t.accentStrong),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(vertical = 6.dp),
                decorationBox = { inner -> if (title.isEmpty()) Text("What needs doing?", style = MaterialTheme.typography.headlineSmall, color = t.textFaint); inner() }
            )
            if (title.length >= MAX_TITLE * 3 / 4) {
                Text("${title.length}/$MAX_TITLE", style = MaterialTheme.typography.labelSmall,
                    color = if (title.length >= MAX_TITLE) Palette.danger else t.textFaint,
                    modifier = Modifier.align(Alignment.End))
            }
            AnimatedVisibility(showNotes) {
                Column {
                    BasicTextField(
                        value = notes, onValueChange = { if (it.length <= MAX_NOTES) notes = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.textMuted),
                        cursorBrush = SolidColor(t.accentStrong),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        decorationBox = { inner -> if (notes.isEmpty()) Text("Add details…", style = MaterialTheme.typography.bodyLarge, color = t.textFaint); inner() }
                    )
                    if (notes.length >= MAX_NOTES * 3 / 4) {
                        Text("${notes.length}/$MAX_NOTES", style = MaterialTheme.typography.labelSmall,
                            color = if (notes.length >= MAX_NOTES) Palette.danger else t.textFaint,
                            modifier = Modifier.align(Alignment.End))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            // Date quick picks
            Label("When")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = Dates.today()
                item { OptionPill("Today", Icons.Rounded.WbSunny, dueDate == today) { dueDate = if (dueDate == today) null else today } }
                item { OptionPill("Tomorrow", Icons.Rounded.WbTwilight, dueDate == today + 1) { dueDate = if (dueDate == today + 1) null else today + 1 } }
                item { OptionPill("Next week", Icons.Rounded.DateRange, dueDate == today + 7) { dueDate = if (dueDate == today + 7) null else today + 7 } }
                item {
                    val custom = dueDate != null && dueDate !in listOf(today, today + 1, today + 7)
                    OptionPill(if (custom) Dates.label(dueDate!!) else "Pick date", Icons.Rounded.CalendarMonth, custom) { showDate = true }
                }
                item { OptionPill("Someday", Icons.Rounded.AllInclusive, dueDate == null) { dueDate = null } }
            }
            AnimatedVisibility(dueDate != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { OptionPill(dueTime?.let { Dates.time(it) } ?: "Add time", Icons.Rounded.Schedule, dueTime != null) { showTime = true } }
                        if (dueTime != null) item { OptionPill("All day", Icons.Rounded.Close, false) { dueTime = null } }
                    }
                    Spacer(Modifier.height(14.dp))
                    Label("Remind me")
                    val presets = listOf<Int?>(null, 0, 15, 60, 1440)
                    val isCustomReminder = reminder != null && reminder !in presets
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { o ->
                            val lbl = if (o == 0 && dueTime == null) "9:00 AM" else Dates.reminderLabel(o).replace(" before", "")
                            OptionPill(
                                text = if (o == null) "Off" else lbl,
                                icon = if (o == null) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                                selected = reminder == o
                            ) {
                                reminder = o
                                if (o != null) ensureFutureDefaultTimeIfToday()
                            }
                        }
                        item {
                            val customText = if (isCustomReminder) {
                                "Custom · " + Dates.reminderLabel(reminder).replace(" before", "")
                            } else "Custom…"
                            OptionPill(
                                text = customText,
                                icon = Icons.Rounded.Tune,
                                selected = isCustomReminder
                            ) { showCustomReminder = true }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Label("Repeat")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("NONE", "DAILY", "WEEKDAYS", "WEEKLY", "MONTHLY", "YEARLY")) { r ->
                            OptionPill(Dates.repeatLabel(r), if (r == "NONE") Icons.Rounded.Block else Icons.Rounded.Repeat, repeat == r) { repeat = r }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Label("Priority")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (3 downTo 0).forEach { p ->
                    val c = Palette.priority[p]
                    val sel = priority == p
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (sel) c.copy(alpha = 0.18f) else t.cardAlt)
                            .border(1.5.dp, if (sel) c else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { priority = p }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Flag, null, tint = c, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Palette.priorityNames[p], style = MaterialTheme.typography.labelMedium, color = t.text)
                    }
                }
            }

            if (categories.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Label("List")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { OptionPill("Inbox", Icons.Rounded.Inbox, categoryId == null) { categoryId = null } }
                    items(categories) { c ->
                        val col = Palette.vivid.getOrElse(c.colorIndex) { Palette.vivid[0] }
                        val sel = categoryId == c.id
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(if (sel) col.copy(alpha = 0.2f) else t.cardAlt)
                                .border(1.5.dp, if (sel) col else Color.Transparent, RoundedCornerShape(50))
                                .clickable { categoryId = c.id }.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("${c.emoji} ${c.name}".trim(), style = MaterialTheme.typography.labelLarge, color = t.text) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Label("Subtasks")
            subs.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    IconButton(onClick = { subs[i] = s.copy(done = !s.done) }, modifier = Modifier.size(34.dp)) {
                        Icon(if (s.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (s.done) t.accentStrong else t.textFaint)
                    }
                    BasicTextField(
                        value = s.title,
                        onValueChange = { updated -> if (updated.length <= MAX_SUBTASK) subs[i] = s.copy(title = updated) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = if (s.done) t.textFaint else t.text,
                            textDecoration = if (s.done) TextDecoration.LineThrough else null
                        ),
                        cursorBrush = SolidColor(t.accentStrong),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    )
                    if (s.title.length >= MAX_SUBTASK * 3 / 4) {
                        Text("${s.title.length}/$MAX_SUBTASK", style = MaterialTheme.typography.labelSmall,
                            color = if (s.title.length >= MAX_SUBTASK) Palette.danger else t.textFaint,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    IconButton(onClick = { subs.removeAt(i) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Close, null, tint = t.textFaint, modifier = Modifier.size(18.dp)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.cardAlt).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(Icons.Rounded.Add, null, tint = t.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = newSub, onValueChange = { if (it.length <= MAX_SUBTASK) newSub = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.text),
                    cursorBrush = SolidColor(t.accentStrong), singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { if (newSub.isNotBlank()) { subs.add(Subtask(taskId = 0, title = newSub.trim().take(MAX_SUBTASK))); newSub = "" } }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (newSub.isEmpty()) Text("Add a step", style = MaterialTheme.typography.bodyLarge, color = t.textFaint); inner() }
                )
                if (newSub.length >= MAX_SUBTASK * 3 / 4) {
                    Text("${newSub.length}/$MAX_SUBTASK", style = MaterialTheme.typography.labelSmall,
                        color = if (newSub.length >= MAX_SUBTASK) Palette.danger else t.textFaint)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!showNotes) {
                    TextButton(onClick = { showNotes = true }) { Icon(Icons.AutoMirrored.Rounded.Notes, null); Spacer(Modifier.width(6.dp)); Text("Details") }
                }
                Spacer(Modifier.weight(1f))
                val enabled = title.isNotBlank()
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (enabled) Brush.horizontalGradient(listOf(t.accentStrong, t.accent.partner)) else SolidColor(t.cardAlt))
                        .clickable(enabled = enabled) { save() }
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) { Text(if (initial == null) "Add task" else "Save", style = MaterialTheme.typography.titleSmall, color = if (enabled) Color.White else t.textFaint) }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (dueDate ?: Dates.today()) * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                showDate = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
            colors = DatePickerDefaults.colors(containerColor = t.card)
        ) { DatePicker(state, colors = DatePickerDefaults.colors(containerColor = t.card)) }
    }
    if (showTime) {
        val init = dueTime ?: ((LocalTime.now().hour + 1).coerceAtMost(23) * 60)
        val state = rememberTimePickerState(initialHour = (init / 60) % 24, initialMinute = init % 60)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = t.card,
            confirmButton = { TextButton(onClick = { dueTime = state.hour * 60 + state.minute; if (reminder == null) reminder = 0; showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) }
        )
    }
    if (showCustomReminder) {
        val initMin = reminder ?: 10
        val initUnit = when {
            initMin > 0 && initMin % 1440 == 0 -> 2 // Days
            initMin > 0 && initMin % 60 == 0 -> 1   // Hours
            else -> 0                               // Minutes
        }
        val initVal = when (initUnit) {
            2 -> (initMin / 1440).coerceAtLeast(1)
            1 -> (initMin / 60).coerceAtLeast(1)
            else -> initMin.coerceAtLeast(1)
        }
        var amountText by remember { mutableStateOf(initVal.toString()) }
        var unit by remember { mutableIntStateOf(initUnit) } // 0 = min, 1 = hr, 2 = day

        AlertDialog(
            onDismissRequest = { showCustomReminder = false },
            containerColor = t.card,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Custom reminder", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        if (dueTime != null) "Remind before ${Dates.time(dueTime!!)}"
                        else "Remind before 9:00 AM (or add a due time)",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textMuted
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) amountText = v },
                        singleLine = true,
                        label = { Text("How long before?") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Minutes", "Hours", "Days").forEachIndexed { idx, label ->
                            SelectChip(label, unit == idx) { unit = idx }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = amountText.toIntOrNull()?.coerceIn(1, 999) ?: 10
                    val multiplier = when (unit) { 2 -> 1440; 1 -> 60; else -> 1 }
                    reminder = n * multiplier
                    ensureFutureDefaultTimeIfToday()
                    showCustomReminder = false
                }) { Text("Set reminder") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomReminder = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = tidy.textMuted, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
}

@Composable
fun OptionPill(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val t = tidy
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) t.accentStrong else t.cardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) (if (t.dark) Color(0xFF121214) else Color.White) else t.textMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) (if (t.dark) Color(0xFF121214) else Color.White) else t.text)
    }
}
