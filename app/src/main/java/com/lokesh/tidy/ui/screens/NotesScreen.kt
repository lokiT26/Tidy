package com.lokesh.tidy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lokesh.tidy.data.Note
import com.lokesh.tidy.ui.components.*
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CheckItem(val done: Boolean, val text: String)

fun parseChecklist(content: String): List<CheckItem> = content.lines().filter { it.isNotEmpty() }.map {
    when {
        it.startsWith("[x] ") -> CheckItem(true, it.removePrefix("[x] "))
        it.startsWith("[ ] ") -> CheckItem(false, it.removePrefix("[ ] "))
        else -> CheckItem(false, it)
    }
}
fun serializeChecklist(items: List<CheckItem>) = items.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    notes: List<Note>,
    onOpen: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    onReorder: (List<Note>) -> Unit,
) {
    val t = tidy
    val haptic = LocalHapticFeedback.current
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(notes, query) {
        if (query.isBlank()) notes else notes.filter { it.title.contains(query, true) || it.content.contains(query, true) }
    }
    val computedPinned = remember(filtered) { filtered.filter { it.pinned } }
    val computedOthers = remember(filtered) { filtered.filter { !it.pinned } }

    var pinned by remember { mutableStateOf(computedPinned) }
    var others by remember { mutableStateOf(computedOthers) }
    var activePinned by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(computedPinned, computedOthers) {
        if (activePinned == null) {
            pinned = computedPinned
            others = computedOthers
        }
    }

    val gridState = rememberLazyStaggeredGridState()
    val reorderState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
        val fk = from.key as? String ?: return@rememberReorderableLazyStaggeredGridState
        val tk = to.key as? String ?: return@rememberReorderableLazyStaggeredGridState
        if (fk.startsWith("p_") && tk.startsWith("p_")) {
            val fi = pinned.indexOfFirst { "p_${it.id}" == fk }
            val ti = pinned.indexOfFirst { "p_${it.id}" == tk }
            if (fi >= 0 && ti >= 0) {
                pinned = pinned.toMutableList().apply { add(ti, removeAt(fi)) }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else if (fk.startsWith("o_") && tk.startsWith("o_")) {
            val fi = others.indexOfFirst { "o_${it.id}" == fk }
            val ti = others.indexOfFirst { "o_${it.id}" == tk }
            if (fi >= 0 && ti >= 0) {
                others = others.toMutableList().apply { add(ti, removeAt(fi)) }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(key = "header", span = StaggeredGridItemSpan.FullLine) {
            Column(Modifier.statusBarsPadding().padding(top = 18.dp)) {
                Text("${notes.size} notes", style = MaterialTheme.typography.labelLarge, color = t.textMuted)
                Text("Notes", style = MaterialTheme.typography.headlineLarge, color = t.text)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(t.card).border(1.dp, t.divider, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = t.textMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.text),
                        cursorBrush = SolidColor(t.accentStrong), modifier = Modifier.weight(1f),
                        decorationBox = { inner -> if (query.isEmpty()) Text("Search your notes", style = MaterialTheme.typography.bodyLarge, color = t.textFaint); inner() })
                    if (query.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = t.textMuted, modifier = Modifier.size(20.dp).clickable { query = "" })
                }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                EmptyState("📝", if (query.isBlank()) "No notes yet" else "No matches", if (query.isBlank()) "Capture a thought with +" else "Try a different word")
            }
        }
        if (pinned.isNotEmpty()) {
            item(key = "h_pinned", span = StaggeredGridItemSpan.FullLine) { SectionHeader("Pinned", pinned.size) }
            items(pinned, key = { "p_${it.id}" }) { n ->
                val canTarget = activePinned == null || activePinned == true
                ReorderableItem(reorderState, key = "p_${n.id}", enabled = canTarget) { isDragging ->
                    NoteCard(
                        n = n,
                        dragging = isDragging,
                        modifier = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                activePinned = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                activePinned = null
                                onReorder(pinned)
                            }
                        ),
                        onClick = { onOpen(n) },
                        onPinClick = { onTogglePin(n) },
                    )
                }
            }
        }
        if (others.isNotEmpty()) {
            if (pinned.isNotEmpty()) item(key = "h_others", span = StaggeredGridItemSpan.FullLine) { SectionHeader("Others", others.size) }
            items(others, key = { "o_${it.id}" }) { n ->
                val canTarget = activePinned == null || activePinned == false
                ReorderableItem(reorderState, key = "o_${n.id}", enabled = canTarget) { isDragging ->
                    NoteCard(
                        n = n,
                        dragging = isDragging,
                        modifier = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                activePinned = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                activePinned = null
                                onReorder(others)
                            }
                        ),
                        onClick = { onOpen(n) },
                        onPinClick = { onTogglePin(n) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    n: Note,
    dragging: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
) {
    val t = tidy
    val bg = Palette.noteColor(n.colorIndex, t.dark)
    val fg = if (t.dark) t.text else Color(0xFF1C1B22)
    val elevation by animateDpAsState(if (dragging) 16.dp else 0.dp, label = "ne")
    Column(
        modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(24.dp), ambientColor = t.accentStrong, spotColor = t.accentStrong)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .then(if (n.colorIndex == 0) Modifier.border(1.dp, t.divider, RoundedCornerShape(24.dp)) else Modifier)
            .bouncyClick(onClick = onClick, onDoubleClick = onPinClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = if (n.pinned) "Unpin note" else "Pin note",
                tint = if (n.pinned) fg.copy(alpha = 0.65f) else fg.copy(alpha = 0.22f),
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPinClick)
                    .padding(3.dp)
            )
        }
        if (n.isChecklist) {
            val items = parseChecklist(n.content)
            if (n.title.isNotBlank() && items.isNotEmpty()) Spacer(Modifier.height(8.dp))
            items.take(8).forEach {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(if (it.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = fg.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(it.text, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = if (it.done) 0.45f else 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (it.done) TextDecoration.LineThrough else null)
                }
            }
            if (items.size > 8) Text("+${items.size - 8} more", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.5f))
        } else if (n.content.isNotBlank()) {
            if (n.title.isNotBlank()) Spacer(Modifier.height(6.dp))
            Text(n.content, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.75f), maxLines = 10, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Text(SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(n.updatedAt)), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.4f))
    }
}

@Composable
fun NoteEditor(initial: Note, onSave: (Note) -> Unit, onDelete: (Note) -> Unit, onClose: () -> Unit) {
    val t = tidy
    var title by remember { mutableStateOf(initial.title) }
    var content by remember { mutableStateOf(initial.content) }
    var color by remember { mutableIntStateOf(initial.colorIndex) }
    var pinned by remember { mutableStateOf(initial.pinned) }
    var checklist by remember { mutableStateOf(initial.isChecklist) }
    val items = remember { mutableStateListOf<CheckItem>().apply { if (initial.isChecklist) addAll(parseChecklist(initial.content)) } }
    var newItem by remember { mutableStateOf("") }

    fun currentContent() = if (checklist) serializeChecklist(items + listOfNotNull(newItem.takeIf { it.isNotBlank() }?.let { CheckItem(false, it) })) else content
    fun close() {
        val c = currentContent()
        val changed = title != initial.title || c != initial.content || color != initial.colorIndex || pinned != initial.pinned || checklist != initial.isChecklist
        if ((title.isNotBlank() || c.isNotBlank()) && changed) {
            onSave(initial.copy(title = title.trim(), content = c, colorIndex = color, pinned = pinned, isChecklist = checklist, updatedAt = System.currentTimeMillis()))
        }
        onClose()
    }
    BackHandler { close() }

    val bgColor by animateColorAsState(if (color == 0) t.bg else Palette.noteColor(color, t.dark), label = "nbg")
    val fg = if (t.dark || color == 0) t.text else Color(0xFF1C1B22)

    Box(Modifier.fillMaxSize().background(bgColor)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = fg) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    if (!checklist) { items.clear(); items.addAll(content.lines().filter { it.isNotBlank() }.map { CheckItem(false, it.trim()) }) }
                    else { content = items.joinToString("\n") { it.text } }
                    checklist = !checklist
                }) { Icon(if (checklist) Icons.AutoMirrored.Rounded.Notes else Icons.Rounded.Checklist, "Checklist", tint = fg) }
                IconButton(onClick = { pinned = !pinned }) { Icon(Icons.Rounded.PushPin, "Pin", tint = if (pinned) t.accentStrong else fg.copy(alpha = 0.45f)) }
                if (initial.id != 0L) {
                    ConfirmDeleteButton(onConfirmDelete = { onDelete(initial); onClose() }, iconTint = fg)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                BasicTextField(title, { title = it }, textStyle = MaterialTheme.typography.headlineMedium.copy(color = fg), cursorBrush = SolidColor(t.accentStrong),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (title.isEmpty()) Text("Title", style = MaterialTheme.typography.headlineMedium, color = fg.copy(alpha = 0.3f)); inner() })
                Spacer(Modifier.height(12.dp))
                if (!checklist) {
                    BasicTextField(content, { content = it }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg.copy(alpha = 0.85f)), cursorBrush = SolidColor(t.accentStrong),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 300.dp),
                        decorationBox = { inner -> if (content.isEmpty()) Text("Start writing…", style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.3f)); inner() })
                } else {
                    items.forEachIndexed { i, it ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { items[i] = it.copy(done = !it.done) }, modifier = Modifier.size(36.dp)) {
                                Icon(if (it.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = if (it.done) t.accentStrong else fg.copy(alpha = 0.5f))
                            }
                            BasicTextField(it.text, { v -> items[i] = it.copy(text = v) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg.copy(alpha = if (it.done) 0.4f else 0.9f), textDecoration = if (it.done) TextDecoration.LineThrough else null),
                                cursorBrush = SolidColor(t.accentStrong), modifier = Modifier.weight(1f))
                            IconButton(onClick = { items.removeAt(i) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, null, tint = fg.copy(alpha = 0.35f), modifier = Modifier.size(18.dp)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = fg.copy(alpha = 0.5f)) }
                        BasicTextField(newItem, { newItem = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg), cursorBrush = SolidColor(t.accentStrong),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { if (newItem.isNotBlank()) { items.add(CheckItem(false, newItem.trim())); newItem = "" } }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner -> if (newItem.isEmpty()) Text("List item", style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.3f)); inner() })
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
            // Colour picker
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(Palette.pastels) { i, _ ->
                    val sel = color == i
                    val scale by animateFloatAsState(if (sel) 1.12f else 1f, label = "cs")
                    Box(
                        Modifier.size((36 * scale).dp).clip(CircleShape).background(Palette.noteColor(i, t.dark))
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) t.accentStrong else fg.copy(alpha = 0.15f), CircleShape)
                            .clickable { color = i },
                        contentAlignment = Alignment.Center
                    ) { if (sel) Icon(Icons.Rounded.Check, null, tint = fg, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}
