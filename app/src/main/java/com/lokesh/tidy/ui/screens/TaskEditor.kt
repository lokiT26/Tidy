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
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private const val MAX_TITLE = 150
private const val MAX_NOTES = 2000
private const val MAX_SUBTASK = 120

private data class EditableSubtask(
    val uid: Long,
    val sub: Subtask,
)

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
    val isExistingTask = initial != null && initial.task.id != 0L
    val base = initial?.task?.let { draft ->
        if (isExistingTask) draft
        else draft.copy(
            dueDate = draft.dueDate ?: defaultDate,
            categoryId = draft.categoryId ?: validDefaultCat,
            priority = if (draft.priority != 0) draft.priority else defaultPriority.coerceIn(0, 3),
        )
    } ?: Task(
        title = "",
        dueDate = defaultDate,
        categoryId = validDefaultCat,
        priority = defaultPriority.coerceIn(0, 3),
    )
    var title by remember { mutableStateOf(base.title.take(MAX_TITLE)) }
    var notes by remember { mutableStateOf(base.notes.take(MAX_NOTES)) }
    var dueDate by remember { mutableStateOf(base.dueDate) }
    var dueTime by remember {
        mutableStateOf(
            if (base.dueDate != null && base.dueTime == null && base.reminderOffset != null) 9 * 60
            else base.dueTime
        )
    }
    var reminder by remember { mutableStateOf(base.reminderOffset) }
    var priority by remember { mutableIntStateOf(base.priority) }
    var categoryId by remember { mutableStateOf(base.categoryId) }
    var repeat by remember { mutableStateOf(base.repeat) }
    var nextSubUid by remember { mutableLongStateOf(1L) }
    val subs = remember {
        mutableStateListOf<EditableSubtask>().apply {
            val initList = initial?.subtasks?.sortedBy { it.sortOrder } ?: emptyList()
            initList.forEach { add(EditableSubtask(nextSubUid++, it)) }
        }
    }
    var newSub by remember { mutableStateOf("") }
    var newSubResetToken by remember { mutableIntStateOf(0) }
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
        val cleanedSubs = subs.map { it.sub.copy(title = it.sub.title.trim().take(MAX_SUBTASK)) }.filter { it.title.isNotEmpty() }
        val finalSubs = if (newSub.isNotBlank()) cleanedSubs + Subtask(taskId = 0, title = newSub.trim().take(MAX_SUBTASK)) else cleanedSubs
        onSave(base.copy(title = title.trim().take(MAX_TITLE), notes = notes.trim().take(MAX_NOTES), dueDate = dueDate, dueTime = if (dueDate == null) null else dueTime,
            reminderOffset = if (dueDate == null) null else reminder, priority = priority, categoryId = categoryId, repeat = if (dueDate == null) "NONE" else repeat), finalSubs)
    }

    LaunchedEffect(Unit) { if (!isExistingTask && title.isEmpty()) focus.requestFocus() }

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
                Text(if (!isExistingTask) "New task" else "Edit task", style = MaterialTheme.typography.labelLarge, color = t.textMuted, modifier = Modifier.weight(1f))
                if (isExistingTask && onDelete != null) {
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
                        item { OptionPill(dueTime?.let { "Due · ${Dates.time(it)}" } ?: "Add time", Icons.Rounded.Schedule, dueTime != null) { showTime = true } }
                        if (dueTime != null) item { OptionPill("All day", Icons.Rounded.Close, false) { dueTime = null; reminder = null } }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 2.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Remind me", style = MaterialTheme.typography.labelMedium, color = t.textMuted)
                        val preview = if (dueTime != null) Dates.reminderPreview(dueDate, dueTime, reminder) else null
                        if (preview != null) {
                            Text("🔔 Notifies $preview", style = MaterialTheme.typography.labelSmall, color = t.accentStrong)
                        }
                    }
                    if (dueTime == null) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                OptionPill("Off", Icons.Rounded.NotificationsOff, true) {}
                            }
                            item {
                                OptionPill("Remind at 9:00 AM", Icons.Rounded.NotificationsActive, false) {
                                    val now = LocalTime.now()
                                    val nowMin = now.hour * 60 + now.minute
                                    dueTime = if (dueDate == Dates.today() && nowMin >= 9 * 60) {
                                        ((now.hour + 1).coerceAtMost(23)) * 60
                                    } else {
                                        9 * 60
                                    }
                                    reminder = 0
                                }
                            }
                            item {
                                OptionPill("Pick time…", Icons.Rounded.Schedule, false) { showTime = true }
                            }
                        }
                    } else {
                        val presets = listOf<Int?>(null, 0, 15, 60, 1440)
                        val isCustomReminder = reminder != null && reminder !in presets
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets) { o ->
                                val lbl = when (o) {
                                    null -> "Off"
                                    0 -> "At due time"
                                    else -> Dates.reminderLabel(o)
                                }
                                OptionPill(
                                    text = lbl,
                                    icon = if (o == null) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                                    selected = reminder == o
                                ) {
                                    reminder = o
                                }
                            }
                            item {
                                val customText = if (isCustomReminder) {
                                    "Custom · " + Dates.reminderLabel(reminder)
                                } else "Custom…"
                                OptionPill(
                                    text = customText,
                                    icon = Icons.Rounded.Tune,
                                    selected = isCustomReminder
                                ) { showCustomReminder = true }
                            }
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
            subs.forEachIndexed { i, item ->
                key(item.uid) {
                    val s = item.sub
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        IconButton(onClick = { subs[i] = item.copy(sub = s.copy(done = !s.done)) }, modifier = Modifier.size(34.dp)) {
                            Icon(if (s.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (s.done) t.accentStrong else t.textFaint)
                        }
                        SingleLineSubtaskField(
                            initialValue = s.title,
                            resetToken = 0,
                            onValueChange = { updated ->
                                val idx = subs.indexOfFirst { it.uid == item.uid }
                                if (idx >= 0) subs[idx] = subs[idx].copy(sub = subs[idx].sub.copy(title = updated))
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = if (s.done) t.textFaint else t.text,
                                textDecoration = if (s.done) TextDecoration.LineThrough else null
                            ),
                            cursorBrush = SolidColor(t.accentStrong),
                            imeAction = ImeAction.Done,
                            onImeAction = null,
                            placeholder = null,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        )
                        if (s.title.length >= MAX_SUBTASK * 3 / 4) {
                            Text("${s.title.length}/$MAX_SUBTASK", style = MaterialTheme.typography.labelSmall,
                                color = if (s.title.length >= MAX_SUBTASK) Palette.danger else t.textFaint,
                                modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        IconButton(
                            onClick = {
                                val idx = subs.indexOfFirst { it.uid == item.uid }
                                if (idx >= 0) subs.removeAt(idx)
                            },
                            modifier = Modifier.size(34.dp)
                        ) { Icon(Icons.Rounded.Close, null, tint = t.textFaint, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.cardAlt).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(Icons.Rounded.Add, null, tint = t.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                SingleLineSubtaskField(
                    initialValue = "",
                    resetToken = newSubResetToken,
                    onValueChange = { newSub = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.text),
                    cursorBrush = SolidColor(t.accentStrong),
                    imeAction = ImeAction.Next,
                    onImeAction = {
                        if (newSub.isNotBlank()) {
                            subs.add(EditableSubtask(nextSubUid++, Subtask(taskId = 0, title = newSub.trim().take(MAX_SUBTASK))))
                            newSub = ""
                            newSubResetToken++
                        }
                    },
                    placeholder = "Add a step",
                    modifier = Modifier.weight(1f),
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
                ) { Text(if (!isExistingTask) "Add task" else "Save", style = MaterialTheme.typography.titleSmall, color = if (enabled) Color.White else t.textFaint) }
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
        val initTotal = (reminder ?: 30).coerceAtLeast(0)
        var daysText by remember { mutableStateOf((initTotal / 1440).toString()) }
        var hoursText by remember { mutableStateOf(((initTotal % 1440) / 60).toString()) }
        var minsText by remember { mutableStateOf((initTotal % 60).toString()) }

        val dVal = daysText.toIntOrNull()?.coerceIn(0, 365) ?: 0
        val hVal = hoursText.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val mVal = minsText.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val totalMinutes = dVal * 1440 + hVal * 60 + mVal

        AlertDialog(
            onDismissRequest = { showCustomReminder = false },
            containerColor = t.card,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Custom reminder", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "How long before ${dueTime?.let { Dates.time(it) } ?: "9:00 AM"}?",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textMuted
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DurationStepperColumn(
                            label = "Days",
                            valueText = daysText,
                            maxVal = 365,
                            onValueChange = { daysText = it },
                            modifier = Modifier.weight(1f)
                        )
                        DurationStepperColumn(
                            label = "Hours",
                            valueText = hoursText,
                            maxVal = 23,
                            onValueChange = { hoursText = it },
                            modifier = Modifier.weight(1f)
                        )
                        DurationStepperColumn(
                            label = "Minutes",
                            valueText = minsText,
                            maxVal = 59,
                            onValueChange = { minsText = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    val preview = Dates.reminderPreview(dueDate, dueTime, totalMinutes)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(t.accentSoft)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (totalMinutes == 0) "🔔 At due time" + (preview?.let { " ($it)" } ?: "")
                            else "🔔 ${Dates.reminderLabel(totalMinutes)}" + (preview?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = t.accentStrong
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reminder = totalMinutes
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
private fun DurationStepperColumn(
    label: String,
    valueText: String,
    maxVal: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = tidy
    val cur = valueText.toIntOrNull()?.coerceIn(0, maxVal) ?: 0
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(t.cardAlt)
            .border(1.dp, t.divider, RoundedCornerShape(18.dp))
            .padding(vertical = 6.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = { onValueChange(((cur + 1).coerceAtMost(maxVal)).toString()) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Increase $label", tint = t.textMuted)
        }
        BasicTextField(
            value = valueText,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(3)
                if (digits.isEmpty()) {
                    onValueChange("")
                } else {
                    val v = digits.toIntOrNull()?.coerceIn(0, maxVal) ?: 0
                    onValueChange(v.toString())
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = t.text, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(t.accentStrong),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (valueText.isEmpty()) {
                        Text("0", style = MaterialTheme.typography.titleLarge, color = t.textFaint, textAlign = TextAlign.Center)
                    }
                    inner()
                }
            }
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = t.textMuted)
        IconButton(
            onClick = { onValueChange(((cur - 1).coerceAtLeast(0)).toString()) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Decrease $label", tint = t.textMuted)
        }
    }
}

@Composable
private fun SingleLineSubtaskField(
    initialValue: String,
    resetToken: Int,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    cursorBrush: Brush,
    imeAction: ImeAction,
    onImeAction: (() -> Unit)?,
    placeholder: String?,
    modifier: Modifier = Modifier,
) {
    val t = tidy
    val tfState = rememberTextFieldState(initialText = initialValue)
    val scrollState = rememberScrollState()
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var boxWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val edgeZonePx = with(density) { 28.dp.toPx() }
    val scrollSpeedPxPerSec = with(density) { 240.dp.toPx() }
    var autoScrollDir by remember { mutableIntStateOf(0) }
    var anchorXInBox by remember { mutableFloatStateOf(0f) }
    var programmaticCursor by remember { mutableIntStateOf(-1) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnImeAction by rememberUpdatedState(onImeAction)

    // Only reset when resetToken increments (when "Add a step" submits and clears to empty) — never fight active typing!
    LaunchedEffect(resetToken) {
        if (resetToken > 0) {
            autoScrollDir = 0
            tfState.edit {
                replace(0, length, "")
                selection = TextRange(0)
            }
            scrollState.scrollTo(0)
        }
    }

    LaunchedEffect(tfState) {
        var prevText = tfState.text.toString()
        var prevOffset = tfState.selection.start
        var dragSteps = 0
        var lastDir = 0

        snapshotFlow { tfState.text.toString() to tfState.selection }.collect { (txt, sel) ->
            if (txt.contains("\n")) {
                val cleaned = txt.replace("\n", " ").take(MAX_SUBTASK)
                tfState.edit { replace(0, length, cleaned) }
                currentOnValueChange(cleaned)
                currentOnImeAction?.invoke()
                prevText = tfState.text.toString()
                prevOffset = tfState.selection.start
                autoScrollDir = 0
                return@collect
            }
            if (txt.length > MAX_SUBTASK) {
                val clamped = txt.take(MAX_SUBTASK)
                tfState.edit { replace(0, length, clamped) }
                currentOnValueChange(clamped)
                prevText = clamped
                prevOffset = tfState.selection.start
                return@collect
            }
            val curOffset = sel.start
            if (txt != prevText) {
                prevText = txt
                prevOffset = curOffset
                dragSteps = 0
                autoScrollDir = 0
                currentOnValueChange(txt)
                return@collect
            }
            if (curOffset == programmaticCursor) {
                prevOffset = curOffset
                return@collect
            }
            val diff = curOffset - prevOffset
            prevOffset = curOffset
            if (!sel.collapsed || diff == 0) {
                if (!sel.collapsed) autoScrollDir = 0
                return@collect
            }
            val dir = if (diff > 0) 1 else -1
            if (kotlin.math.abs(diff) <= 6) {
                dragSteps = if (dir == lastDir) dragSteps + 1 else 1
                lastDir = dir
            } else {
                dragSteps = 0
                lastDir = dir
                autoScrollDir = 0
                return@collect
            }

            val layout = layoutResult ?: return@collect
            if (boxWidthPx <= 0 || scrollState.maxValue <= 0) return@collect
            val rect = runCatching { layout.getCursorRect(curOffset.coerceIn(0, txt.length)) }.getOrNull() ?: return@collect
            val cursorXInBox = rect.left - scrollState.value

            if (dragSteps >= 2) {
                if (dir > 0 && cursorXInBox >= boxWidthPx - edgeZonePx && curOffset < txt.length) {
                    anchorXInBox = cursorXInBox.coerceIn(boxWidthPx - edgeZonePx, boxWidthPx.toFloat() - 2f)
                    autoScrollDir = 1
                } else if (dir < 0 && cursorXInBox <= edgeZonePx && curOffset > 0) {
                    anchorXInBox = cursorXInBox.coerceIn(2f, edgeZonePx)
                    autoScrollDir = -1
                } else if (
                    (autoScrollDir == 1 && (dir < 0 || cursorXInBox < boxWidthPx - edgeZonePx * 1.4f)) ||
                    (autoScrollDir == -1 && (dir > 0 || cursorXInBox > edgeZonePx * 1.4f))
                ) {
                    autoScrollDir = 0
                }
            }
        }
    }

    LaunchedEffect(autoScrollDir) {
        if (autoScrollDir == 0) return@LaunchedEffect
        var floatScroll = scrollState.value.toFloat()
        var lastNanos = 0L
        while (autoScrollDir != 0) {
            val dtSec = withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) 0.016f else ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                lastNanos = nanos
                dt
            }
            val layout = layoutResult ?: break
            val maxScroll = scrollState.maxValue
            if (maxScroll <= 0) {
                autoScrollDir = 0
                break
            }
            floatScroll = (floatScroll + autoScrollDir * scrollSpeedPxPerSec * dtSec).coerceIn(0f, maxScroll.toFloat())
            val targetScrollInt = floatScroll.toInt()
            if (targetScrollInt != scrollState.value) {
                scrollState.scrollTo(targetScrollInt)
            }
            val txt = tfState.text.toString()
            val cur = tfState.selection.start
            val curRect = runCatching { layout.getCursorRect(cur.coerceIn(0, txt.length)) }.getOrNull()
            val probeY = curRect?.center?.y ?: 10f
            val targetTextX = if (autoScrollDir > 0 && targetScrollInt >= maxScroll) {
                layout.size.width.toFloat()
            } else if (autoScrollDir < 0 && targetScrollInt <= 0) {
                0f
            } else {
                anchorXInBox + scrollState.value
            }
            val nextOffset = layout.getOffsetForPosition(Offset(targetTextX, probeY)).coerceIn(0, txt.length)
            if (nextOffset != cur) {
                programmaticCursor = nextOffset
                tfState.edit { selection = TextRange(nextOffset) }
            }
            if ((autoScrollDir > 0 && targetScrollInt >= maxScroll && nextOffset >= txt.length) ||
                (autoScrollDir < 0 && targetScrollInt <= 0 && nextOffset <= 0)
            ) {
                autoScrollDir = 0
                break
            }
        }
    }

    BasicTextField(
        state = tfState,
        lineLimits = TextFieldLineLimits.SingleLine,
        scrollState = scrollState,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        onKeyboardAction = if (onImeAction != null) {
            KeyboardActionHandler { currentOnImeAction?.invoke() }
        } else null,
        onTextLayout = { getResult -> layoutResult = getResult() },
        modifier = modifier
            .onSizeChanged { boxWidthPx = it.width }
            .onFocusChanged { if (!it.isFocused) autoScrollDir = 0 },
        decorator = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (placeholder != null && tfState.text.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = t.textFaint)
                }
                inner()
            }
        }
    )
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
