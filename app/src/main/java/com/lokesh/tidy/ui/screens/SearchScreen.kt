package com.lokesh.tidy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Note
import com.lokesh.tidy.data.Subtask
import com.lokesh.tidy.data.Task
import com.lokesh.tidy.data.TaskWithSubtasks
import com.lokesh.tidy.ui.components.*
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy

@Composable
fun SearchScreen(
    tasks: List<TaskWithSubtasks>, notes: List<Note>, categories: List<Category>,
    onToggle: (Task) -> Unit, onOpenTask: (TaskWithSubtasks) -> Unit, onDeleteTask: (TaskWithSubtasks) -> Unit,
    onOpenNote: (Note) -> Unit, onSubtask: (Subtask) -> Unit, onBack: () -> Unit,
) {
    val t = tidy
    BackHandler(onBack = onBack)
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 tasks, 2 notes, 3 high priority
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val catMap = categories.associateBy { it.id }
    val tr = if (filter == 2) emptyList() else tasks.filter {
        (q.isBlank() || it.task.title.contains(q, true) || it.task.notes.contains(q, true) || it.subtasks.any { s -> s.title.contains(q, true) }) &&
            (filter != 3 || it.task.priority == 3) && (q.isNotBlank() || filter == 3)
    }
    val nr = if (filter == 1 || filter == 3 || q.isBlank()) emptyList() else notes.filter { it.title.contains(q, true) || it.content.contains(q, true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
                Spacer(Modifier.width(10.dp))
                Row(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(t.card).padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(q, { q = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.text), cursorBrush = SolidColor(t.accentStrong),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner -> if (q.isEmpty()) Text("Search tasks & notes", style = MaterialTheme.typography.bodyLarge, color = t.textFaint); inner() })
                    if (q.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = t.textMuted, modifier = Modifier.clickable { q = "" })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf("All", "Tasks", "Notes", "🚩 High").forEachIndexed { i, s -> SelectChip(s, filter == i) { filter = i } }
            }
        }
        if (q.isBlank() && filter != 3) item { EmptyState("🔍", "Find anything", "Search titles, details, subtasks and notes") }
        else if (tr.isEmpty() && nr.isEmpty()) item { EmptyState("🤷", "No results", "Try another keyword") }
        if (tr.isNotEmpty()) {
            item { SectionHeader("Tasks", tr.size) }
            items(tr, key = { "t" + it.task.id }) {
                SwipeableTaskCard(
                    item = it,
                    category = it.task.categoryId?.let(catMap::get),
                    showDate = true,
                    isDragging = false,
                    onToggle = { onToggle(it.task) },
                    onOpen = { onOpenTask(it) },
                    onDelete = { onDeleteTask(it) },
                    onSubtask = onSubtask,
                )
            }
        }
        if (nr.isNotEmpty()) {
            item { SectionHeader("Notes", nr.size) }
            items(nr, key = { "n" + it.id }) { n ->
                NoteCard(
                    n = n,
                    onClick = { onOpenNote(n) }
                )
            }
        }
    }
}

@Composable
fun CategoryDialog(initial: Category?, onSave: (Category) -> Unit, onDelete: ((Category) -> Unit)?, onDismiss: () -> Unit) {
    val t = tidy
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "📁") }
    var color by remember { mutableIntStateOf(initial?.colorIndex ?: 1) }
    val emojis = listOf("📁", "🌿", "💼", "💪", "🏠", "🛒", "📚", "🎯", "💡", "✈️", "🎨", "💰", "❤️", "🎵", "🧘", "🍳")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.card,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial == null) "New list" else "Edit list", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (initial != null && onDelete != null) {
                    ConfirmDeleteButton(onConfirmDelete = { onDelete(initial) })
                }
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    supportingText = if (name.length >= 22) ({ Text("${name.length}/30") }) else null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    emojis.forEach { e ->
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (emoji == e) t.accentSoft else t.cardAlt).clickable { emoji = e }, contentAlignment = Alignment.Center) { Text(e) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1 until Palette.vivid.size).forEach { i ->
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(Palette.vivid[i]).clickable { color = i }, contentAlignment = Alignment.Center) {
                            if (color == i) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave((initial ?: Category(name = "")).copy(name = name.trim().take(30), emoji = emoji, colorIndex = color)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
