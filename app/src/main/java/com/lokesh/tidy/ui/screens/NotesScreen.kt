package com.lokesh.tidy.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.ScrollableState
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lokesh.tidy.data.Note
import com.lokesh.tidy.data.Subtask
import com.lokesh.tidy.data.Task
import com.lokesh.tidy.data.TaskWithSubtasks
import com.lokesh.tidy.ui.components.*
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState
import sh.calvin.reorderable.rememberScroller
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CheckItem(val done: Boolean, val text: String)

private const val IN_ITEM_NL = "\u200B\n"
private const val ZWSP = "\u200B"

data class EditableCheckItem(
    val uid: Long,
    val done: Boolean,
    val tfv: TextFieldValue,
) {
    val cleanText: String get() = tfv.text.replace(ZWSP, "")
}

fun makeEditableCheckItem(
    uid: Long,
    done: Boolean,
    text: String,
    cursorInClean: Int = text.replace(ZWSP, "").length,
): EditableCheckItem {
    val clean = text.replace(ZWSP, "")
    val clamped = cursorInClean.coerceIn(0, clean.length)
    return EditableCheckItem(
        uid = uid,
        done = done,
        tfv = TextFieldValue(
            text = ZWSP + clean,
            selection = TextRange(1 + clamped)
        )
    )
}

/**
 * Pure state-transition helper for checklist editing:
 * - If leading sentinel (\u200B) is removed (Backspace at start / on empty item), merges into item (index - 1)
 *   and places cursor at the very end of the previous item's text.
 * - If Enter (\n) is typed on an empty line inside item [index], strips that empty line and creates/focuses item (index + 1).
 * Returns the UID of the item that should receive focus (or null if focus stays on the current item).
 */
fun applyChecklistValueChange(
    items: MutableList<EditableCheckItem>,
    index: Int,
    newTfv: TextFieldValue,
    allocateUid: () -> Long,
): Long? {
    if (index !in items.indices) return null
    val item = items[index]

    // 1. Backspace at start of item (sentinel deleted)
    if (!newTfv.text.startsWith(ZWSP)) {
        val remainder = newTfv.text.replace(ZWSP, "")
        return if (index > 0) {
            val prev = items[index - 1]
            val prevClean = prev.cleanText
            val merged = prevClean + remainder
            items[index - 1] = makeEditableCheckItem(prev.uid, prev.done, merged, cursorInClean = prevClean.length)
            items.removeAt(index)
            prev.uid
        } else {
            items[0] = makeEditableCheckItem(item.uid, item.done, remainder, cursorInClean = 0)
            null
        }
    }

    // 2. Check if Enter (\n) was just typed on an already-empty line
    val oldClean = item.cleanText
    val newClean = newTfv.text.removePrefix(ZWSP).replace(ZWSP, "")
    val newCleanCursor = (newTfv.selection.start - 1).coerceIn(0, newClean.length)
    val justTypedNewline = newClean.length == oldClean.length + 1 &&
        newCleanCursor > 0 &&
        newClean[newCleanCursor - 1] == '\n' &&
        newClean.removeRange(newCleanCursor - 1, newCleanCursor) == oldClean

    if (justTypedNewline) {
        val oldCursor = newCleanCursor - 1
        val lineStart = oldClean.lastIndexOf('\n', (oldCursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = oldClean.indexOf('\n', oldCursor).let { if (it == -1) oldClean.length else it }
        val currentLine = oldClean.substring(lineStart, lineEnd)

        if (currentLine.isBlank()) {
            val beforePart = if (lineStart > 0) oldClean.substring(0, lineStart - 1) else ""
            val afterPart = if (lineEnd < oldClean.length) oldClean.substring(lineEnd + 1) else ""
            items[index] = makeEditableCheckItem(item.uid, item.done, beforePart, cursorInClean = beforePart.length)

            return if (beforePart.isEmpty() && afterPart.isEmpty() && index + 1 < items.size) {
                val next = items[index + 1]
                items[index + 1] = makeEditableCheckItem(next.uid, next.done, next.cleanText, cursorInClean = next.cleanText.length)
                next.uid
            } else {
                val created = makeEditableCheckItem(allocateUid(), false, afterPart, cursorInClean = 0)
                items.add(index + 1, created)
                created.uid
            }
        }
    }

    // 3. Normal edit: preserve IME composition if sentinel is intact, clamping cursor to >= 1
    val hasExtraSentinels = newTfv.text.substring(1).contains(ZWSP)
    val updatedTfv = if (!hasExtraSentinels) {
        val s = newTfv.selection.start.coerceAtLeast(1)
        val e = newTfv.selection.end.coerceAtLeast(1)
        if (s == newTfv.selection.start && e == newTfv.selection.end) newTfv
        else newTfv.copy(selection = TextRange(s, e))
    } else {
        val clean = newTfv.text.replace(ZWSP, "")
        val s = (newTfv.selection.start - 1).coerceIn(0, clean.length)
        val e = (newTfv.selection.end - 1).coerceIn(0, clean.length)
        TextFieldValue(text = ZWSP + clean, selection = TextRange(1 + s, 1 + e))
    }
    items[index] = item.copy(tfv = updatedTfv)
    return null
}

fun parseChecklist(content: String): List<CheckItem> {
    if (content.isBlank()) return emptyList()
    val lines = content.replace(IN_ITEM_NL, "\n").lines()
    val hasMarkers = lines.any { it.startsWith("[x] ") || it.startsWith("[ ] ") }
    if (!hasMarkers) {
        return content
            .replace(IN_ITEM_NL, "\u0000")
            .split("\n")
            .map { it.replace("\u0000", "\n").replace("\u200B", "").trim() }
            .filter { it.isNotEmpty() }
            .map { CheckItem(false, it) }
    }
    val result = mutableListOf<CheckItem>()
    for (raw in lines) {
        val line = raw.replace("\u200B", "")
        when {
            line.startsWith("[x] ") -> result.add(CheckItem(true, line.removePrefix("[x] ")))
            line.startsWith("[ ] ") -> result.add(CheckItem(false, line.removePrefix("[ ] ")))
            result.isNotEmpty() -> {
                val last = result.removeAt(result.lastIndex)
                result.add(last.copy(text = if (line.isEmpty()) last.text else "${last.text}\n$line"))
            }
            line.isNotEmpty() -> result.add(CheckItem(false, line))
        }
    }
    return result
}

fun serializeChecklist(items: List<CheckItem>) = items.joinToString("\n") {
    (if (it.done) "[x] " else "[ ] ") + it.text.replace("\u200B", "")
}

fun convertNoteToTask(
    title: String,
    content: String,
    isChecklist: Boolean,
    checklistItems: List<CheckItem>,
): TaskWithSubtasks {
    val cleanTitle = title.trim()
    return if (isChecklist) {
        val nonBlank = checklistItems.filter { it.text.isNotBlank() }
        val taskTitle = when {
            cleanTitle.isNotEmpty() -> cleanTitle.take(150)
            nonBlank.isNotEmpty() -> nonBlank.first().text.replace("\n", " · ").trim().take(150)
            else -> "Untitled task"
        }
        val subItems = if (cleanTitle.isNotEmpty()) nonBlank else nonBlank.drop(1)
        val subtasks = subItems.mapIndexed { idx, ci ->
            Subtask(
                taskId = 0,
                title = ci.text.replace("\n", " · ").trim().take(120),
                done = ci.done,
                sortOrder = idx
            )
        }
        TaskWithSubtasks(Task(title = taskTitle), subtasks)
    } else {
        val lines = content.replace("\u200B", "").trim().lines()
        val taskTitle = if (cleanTitle.isNotEmpty()) {
            cleanTitle.take(150)
        } else {
            lines.firstOrNull { it.isNotBlank() }?.trim()?.take(150) ?: "Untitled task"
        }
        val taskNotes = if (cleanTitle.isNotEmpty()) {
            content.replace("\u200B", "").trim().take(2000)
        } else {
            lines.dropWhile { it.isBlank() }.drop(1).joinToString("\n").trim().take(2000)
        }
        TaskWithSubtasks(Task(title = taskTitle, notes = taskNotes), emptyList())
    }
}

/**
 * Fast 3-pass separable box blur (converges to Gaussian blur) on a small downscaled ARGB_8888 bitmap.
 * Works on all Android versions (including Android 11 / API 30 where RenderEffect is unavailable).
 */
private fun gaussianBoxBlur(bmp: Bitmap, radius: Int = 3) {
    val w = bmp.width
    val h = bmp.height
    if (w < 3 || h < 3) return
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val div = 2 * radius + 1

    repeat(3) {
        // Horizontal pass
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0
                for (k in -radius..radius) {
                    val px = pixels[row + (x + k).coerceIn(0, w - 1)]
                    a += (px ushr 24) and 0xFF
                    r += (px ushr 16) and 0xFF
                    g += (px ushr 8) and 0xFF
                    b += px and 0xFF
                }
                temp[row + x] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            }
        }
        // Vertical pass
        for (x in 0 until w) {
            for (y in 0 until h) {
                var r = 0; var g = 0; var b = 0; var a = 0
                for (k in -radius..radius) {
                    val py = (y + k).coerceIn(0, h - 1)
                    val px = temp[py * w + x]
                    a += (px ushr 24) and 0xFF
                    r += (px ushr 16) and 0xFF
                    g += (px ushr 8) and 0xFF
                    b += px and 0xFF
                }
                pixels[y * w + x] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            }
        }
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
}

private class BlurCacheHolder(var key: Any? = null, var w: Int = 0, var h: Int = 0, var bmp: Bitmap? = null) {
    fun clear() {
        bmp?.let { if (!it.isRecycled) it.recycle() }
        bmp = null
        w = 0
        h = 0
        key = null
    }
}

/**
 * Renders the exact laid-out note content into a downscaled offscreen bitmap and applies a real
 * 3-pass Gaussian blur + frosted pastel wash once per note/size/theme change, caching the bitmap
 * so scrolling and animations draw with zero per-frame allocations.
 */
fun Modifier.realNoteBlur(enabled: Boolean, cardBg: Color, fg: Color, isDark: Boolean, cacheKey: Any = Unit): Modifier {
    if (!enabled) return this
    return this.composed {
        val holder = remember { BlurCacheHolder() }
        DisposableEffect(Unit) {
            onDispose { holder.clear() }
        }
        val paint = remember {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        }
        this.drawWithContent {
            val w = size.width.roundToInt()
            val h = size.height.roundToInt()
            if (w <= 4 || h <= 4) return@drawWithContent

            val scale = 6
            val bw = max(6, w / scale)
            val bh = max(6, h / scale)
            val existing = holder.bmp
            val valid = existing != null && !existing.isRecycled && holder.w == bw && holder.h == bh && holder.key == cacheKey

            val bmp = if (valid) {
                existing!!
            } else {
                holder.clear()
                Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { fresh ->
                    val nativeCanvas = android.graphics.Canvas(fresh)
                    nativeCanvas.drawColor(cardBg.toArgb())

                    val composeCanvas = Canvas(nativeCanvas)
                    composeCanvas.save()
                    composeCanvas.scale(bw / size.width, bh / size.height)
                    draw(this, layoutDirection, composeCanvas, size) {
                        this@drawWithContent.drawContent()
                    }
                    composeCanvas.restore()

                    gaussianBoxBlur(fresh, radius = 3)
                    holder.bmp = fresh
                    holder.w = bw
                    holder.h = bh
                    holder.key = cacheKey
                }
            }

            val dst = android.graphics.RectF(0f, 0f, size.width, size.height)
            drawContext.canvas.nativeCanvas.drawBitmap(bmp, null, dst, paint)

            // Frosted pastel veil so the note's colour remains distinct while text is completely unreadable
            drawRoundRect(
                color = cardBg.copy(alpha = if (isDark) 0.34f else 0.30f),
                cornerRadius = CornerRadius(14.dp.toPx())
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    notes: List<Note>,
    pinnedSizeMode: Int = 0,
    othersSizeMode: Int = 0,
    sortOrder: Int = 0,
    onSortOrderChange: (Int) -> Unit = {},
    onActiveColorFilterChange: (Int?) -> Unit = {},
    onOpen: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    onReorder: (List<Note>) -> Unit,
) {
    val t = tidy
    val haptic = LocalHapticFeedback.current
    var query by rememberSaveable { mutableStateOf("") }
    /** null = All colours, 0..7 = specific Palette.pastels colour */
    var colorFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    /** 0 = All types, 1 = Text, 2 = Checklist, 3 = Hidden, 4 = Visible only */
    var typeFilter by rememberSaveable { mutableIntStateOf(0) }
    var showFilterBar by rememberSaveable { mutableStateOf(false) }
    var showSortBar by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(colorFilter) {
        onActiveColorFilterChange(colorFilter)
    }

    val queryFiltered = remember(notes, query) {
        if (query.isBlank()) notes else notes.filter {
            it.title.contains(query, true) || (!it.hidden && it.content.contains(query, true))
        }
    }
    // Pinned notes are untouched by the Others filter/sort (staying fixed at top in manual order)
    val computedPinned = remember(queryFiltered) { queryFiltered.filter { it.pinned } }

    // Others notes apply the colour filter, type filter, and sort order
    val computedOthers = remember(queryFiltered, colorFilter, typeFilter, sortOrder) {
        val base = queryFiltered.filter { !it.pinned }
            .filter { n -> colorFilter == null || n.colorIndex == colorFilter }
            .filter { n ->
                when (typeFilter) {
                    1 -> !n.isChecklist
                    2 -> n.isChecklist
                    3 -> n.hidden
                    4 -> !n.hidden
                    else -> true
                }
            }
        when (sortOrder) {
            1 -> base.sortedByDescending { it.updatedAt }
            2 -> base.sortedByDescending { it.createdAt }
            3 -> base.sortedBy { it.updatedAt }
            4 -> base.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { it.content } })
            else -> base // Custom drag order (sortOrder ASC, updatedAt DESC from Room)
        }
    }

    var pinned by remember { mutableStateOf(computedPinned) }
    var others by remember { mutableStateOf(computedOthers) }
    var activePinned by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(computedPinned, computedOthers) {
        if (activePinned == null) {
            pinned = computedPinned
            others = computedOthers
        }
    }

    val hasActiveFilter = colorFilter != null || typeFilter != 0
    val canDragOthers = sortOrder == 0 && !hasActiveFilter && query.isBlank()

    val gridState = rememberLazyStaggeredGridState()
    val boundedScrollState = remember(gridState) {
        object : ScrollableState by gridState {
            private fun validKeys(): List<String> = when (activePinned) {
                true -> pinned.map { "p_${it.id}" }
                false -> others.map { "o_${it.id}" }
                null -> emptyList()
            }
            override val canScrollForward: Boolean
                get() {
                    val lastKey = validKeys().lastOrNull() ?: return false
                    val info = gridState.layoutInfo
                    val item = info.visibleItemsInfo.find { it.key == lastKey }
                    return gridState.canScrollForward && (item == null || (item.offset.y + item.size.height) > info.viewportEndOffset)
                }
            override val canScrollBackward: Boolean
                get() {
                    val firstKey = validKeys().firstOrNull() ?: return false
                    val info = gridState.layoutInfo
                    val item = info.visibleItemsInfo.find { it.key == firstKey }
                    return gridState.canScrollBackward && (item == null || item.offset.y < info.viewportStartOffset)
                }
        }
    }
    val scroller = rememberScroller(boundedScrollState, (gridState.layoutInfo.viewportSize.height * 0.05f).coerceAtLeast(24f))
    val reorderState = rememberReorderableLazyStaggeredGridState(
        lazyStaggeredGridState = gridState,
        scroller = scroller,
    ) { from, to ->
        val fk = from.key as? String ?: return@rememberReorderableLazyStaggeredGridState
        val tk = to.key as? String ?: return@rememberReorderableLazyStaggeredGridState
        if (fk.startsWith("p_") && tk.startsWith("p_")) {
            val fi = pinned.indexOfFirst { "p_${it.id}" == fk }
            val ti = pinned.indexOfFirst { "p_${it.id}" == tk }
            if (fi >= 0 && ti >= 0) {
                pinned = pinned.toMutableList().apply { add(ti, removeAt(fi)) }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } else if (fk.startsWith("o_") && tk.startsWith("o_") && canDragOthers) {
            val fi = others.indexOfFirst { "o_${it.id}" == fk }
            val ti = others.indexOfFirst { "o_${it.id}" == tk }
            if (fi >= 0 && ti >= 0) {
                others = others.toMutableList().apply { add(ti, removeAt(fi)) }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val sortLabels = listOf("Custom", "Newest edited", "Newest created", "Oldest", "Title A–Z")
    val unpinnedTotal = remember(notes) { notes.count { !it.pinned } }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 0.dp),
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

        if (pinned.isNotEmpty()) {
            item(key = "h_pinned", span = StaggeredGridItemSpan.FullLine) { SectionHeader("Pinned", pinned.size) }
            items(pinned, key = { "p_${it.id}" }) { n ->
                val canTarget = activePinned == null || activePinned == true
                ReorderableItem(reorderState, key = "p_${n.id}", enabled = canTarget) { isDragging ->
                    NoteCard(
                        n = n,
                        sizeMode = pinnedSizeMode,
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

        if (unpinnedTotal > 0 || hasActiveFilter) {
            item(key = "h_others", span = StaggeredGridItemSpan.FullLine) {
                Column {
                    SectionHeader(if (pinned.isNotEmpty()) "Others" else "All notes", others.size) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Minimal Filter icon button inline with Others header
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (showFilterBar || hasActiveFilter) t.accentSoft else Color.Transparent)
                                    .clickable {
                                        showFilterBar = !showFilterBar
                                        if (showFilterBar) showSortBar = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (colorFilter != null) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Palette.vivid.getOrElse(colorFilter!!) { t.accentStrong })
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Icon(
                                    Icons.Rounded.FilterList,
                                    contentDescription = "Filter notes",
                                    tint = if (showFilterBar || hasActiveFilter) t.accentStrong else t.textMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Minimal Sort icon button inline with Others header
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (showSortBar || sortOrder != 0) t.accentSoft else Color.Transparent)
                                    .clickable {
                                        showSortBar = !showSortBar
                                        if (showSortBar) showFilterBar = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.SwapVert,
                                    contentDescription = "Sort notes",
                                    tint = if (showSortBar || sortOrder != 0) t.accentStrong else t.textMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                                if (sortOrder != 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        sortLabels.getOrElse(sortOrder) { "" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = t.accentStrong
                                    )
                                }
                            }
                        }
                    }

                    // Breathable expandable Filter strip
                    AnimatedVisibility(visible = showFilterBar) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(t.card)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    SelectChip("All colours", colorFilter == null) { colorFilter = null }
                                }
                                itemsIndexed(Palette.pastels) { idx, _ ->
                                    val sel = colorFilter == idx
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Palette.noteColor(idx, t.dark))
                                            .border(
                                                width = if (sel) 2.5.dp else 1.dp,
                                                color = if (sel) t.accentStrong else t.divider,
                                                shape = CircleShape
                                            )
                                            .clickable { colorFilter = if (sel) null else idx },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (sel) Icon(Icons.Rounded.Check, null, tint = t.text, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val types = listOf("All types", "Text", "Checklist", "Hidden", "Visible only")
                                itemsIndexed(types) { idx, label ->
                                    SelectChip(label, typeFilter == idx) { typeFilter = idx }
                                }
                            }
                        }
                    }

                    // Breathable expandable Sort strip
                    AnimatedVisibility(visible = showSortBar) {
                        LazyRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(t.card)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(sortLabels) { idx, label ->
                                SelectChip(label, sortOrder == idx) {
                                    onSortOrderChange(idx)
                                    showSortBar = false
                                }
                            }
                        }
                    }
                }
            }

            items(others, key = { "o_${it.id}" }) { n ->
                val canTarget = canDragOthers && (activePinned == null || activePinned == false)
                ReorderableItem(reorderState, key = "o_${n.id}", enabled = canTarget) { isDragging ->
                    NoteCard(
                        n = n,
                        sizeMode = othersSizeMode,
                        dragging = isDragging,
                        modifier = if (canDragOthers) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    activePinned = false
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    activePinned = null
                                    onReorder(others)
                                }
                            )
                        } else Modifier,
                        onClick = { onOpen(n) },
                        onPinClick = { onTogglePin(n) },
                    )
                }
            }
        }

        if (pinned.isEmpty() && others.isEmpty()) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                EmptyState(
                    emoji = "📝",
                    title = if (query.isBlank() && !hasActiveFilter) "No notes yet" else "No matching notes",
                    subtitle = if (query.isBlank() && !hasActiveFilter) "Capture a thought with +" else "Try clearing your filter or search"
                )
            }
        }

        item(key = "bottom_spacer", span = StaggeredGridItemSpan.FullLine) {
            Spacer(Modifier.height(140.dp))
        }
    }
}

@Composable
fun NoteCard(
    n: Note,
    sizeMode: Int = 0,
    dragging: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPinClick: (() -> Unit)? = null,
) {
    val t = tidy
    val bg = Palette.noteColor(n.colorIndex, t.dark)
    val fg = if (t.dark) t.text else Color(0xFF1C1B22)
    val elevation by animateDpAsState(if (dragging) 16.dp else 0.dp, label = "ne")
    val heightMod = when (sizeMode) {
        1 -> Modifier.height(116.dp)
        2 -> Modifier.height(170.dp)
        3 -> Modifier.height(232.dp)
        else -> Modifier
    }
    val maxChecklistLines = when (sizeMode) {
        1 -> 2
        2 -> 4
        3 -> 7
        else -> 8
    }
    val maxBodyLines = when (sizeMode) {
        1 -> 2
        2 -> 5
        3 -> 8
        else -> 10
    }

    val checklistItems = remember(n.content, n.isChecklist) {
        if (n.isChecklist) parseChecklist(n.content) else emptyList()
    }
    val extraChecklistCount = (checklistItems.size - maxChecklistLines).coerceAtLeast(0)
    val cardPad = if (sizeMode == 1) 13.dp else 16.dp

    Box(
        modifier
            .fillMaxWidth()
            .then(heightMod)
            .shadow(elevation, RoundedCornerShape(24.dp), ambientColor = t.accentStrong, spotColor = t.accentStrong)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .then(if (n.colorIndex == 0) Modifier.border(1.dp, t.divider, RoundedCornerShape(24.dp)) else Modifier)
            .bouncyClick(onClick = onClick, onDoubleClick = onPinClick)
    ) {
        // Content layer (measured identically whether hidden or visible; cached Gaussian blur applied when n.hidden)
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (sizeMode != 0) Modifier.fillMaxHeight() else Modifier)
                .realNoteBlur(
                    enabled = n.hidden,
                    cardBg = bg,
                    fg = fg,
                    isDark = t.dark,
                    cacheKey = listOf(n.id, n.updatedAt, n.title, n.content, n.colorIndex, n.isChecklist, sizeMode, t.dark)
                )
                .padding(cardPad)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (sizeMode != 0) Modifier.weight(1f).clip(RoundedCornerShape(4.dp)) else Modifier)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    if (n.title.isNotBlank()) {
                        Text(
                            n.title,
                            style = if (sizeMode == 1) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            color = fg,
                            maxLines = if (sizeMode == 1) 1 else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = if (onPinClick != null) 24.dp else 0.dp)
                        )
                    } else {
                        Spacer(Modifier.weight(1f).height(2.dp))
                    }
                }
                if (n.isChecklist) {
                    if (n.title.isNotBlank() && checklistItems.isNotEmpty()) {
                        Spacer(Modifier.height(if (sizeMode == 1) 3.dp else 6.dp))
                    }
                    checklistItems.take(maxChecklistLines).forEach {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = if (sizeMode == 1) 1.dp else 2.dp)
                        ) {
                            Icon(
                                if (it.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                null,
                                tint = fg.copy(alpha = 0.55f),
                                modifier = Modifier.size(if (sizeMode == 1) 14.5.dp else 16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                it.text.replace("\n", " · "),
                                style = if (sizeMode == 1) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                color = fg.copy(alpha = if (it.done) 0.45f else 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (it.done) TextDecoration.LineThrough else null
                            )
                        }
                    }
                } else if (n.content.isNotBlank()) {
                    if (n.title.isNotBlank()) Spacer(Modifier.height(if (sizeMode == 1) 3.dp else 6.dp))
                    Text(
                        n.content,
                        style = if (sizeMode == 1) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = fg.copy(alpha = 0.75f),
                        maxLines = maxBodyLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(if (sizeMode == 1) 4.dp else 8.dp))
            // Locked footer row: Date on left, '+n more' inline on right so date is never clipped (#2)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(n.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.42f)
                )
                if (n.isChecklist && extraChecklistCount > 0) {
                    Text(
                        "+$extraChecklistCount more",
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.55f)
                    )
                }
            }
        }

        // Subtle frosted eye-off badge centered over blurred notes
        if (n.hidden) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(fg.copy(alpha = if (t.dark) 0.14f else 0.10f))
                    .border(0.8.dp, fg.copy(alpha = 0.16f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VisibilityOff, "Hidden note", tint = fg.copy(alpha = 0.72f), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Hidden", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.72f))
                }
            }
        }

        // Sharp clickable pin icon in top-right corner
        if (onPinClick != null) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = if (n.pinned) "Unpin note" else "Pin note",
                tint = if (n.pinned) fg.copy(alpha = 0.65f) else fg.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPinClick)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun NoteEditor(
    initial: Note,
    onSave: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onClose: () -> Unit,
    onConvertToTask: ((TaskWithSubtasks) -> Unit)? = null,
) {
    val t = tidy
    var title by remember { mutableStateOf(initial.title) }
    var content by remember { mutableStateOf(initial.content) }
    var color by remember { mutableIntStateOf(initial.colorIndex) }
    var pinned by remember { mutableStateOf(initial.pinned) }
    var hidden by remember { mutableStateOf(initial.hidden) }
    var checklist by remember { mutableStateOf(initial.isChecklist) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var nextUid by remember { mutableLongStateOf(1L) }
    fun allocUid(): Long = nextUid++
    val items = remember {
        mutableStateListOf<EditableCheckItem>().apply {
            if (initial.isChecklist) {
                val parsed = parseChecklist(initial.content)
                if (parsed.isEmpty()) {
                    add(makeEditableCheckItem(allocUid(), false, ""))
                } else {
                    parsed.forEach { add(makeEditableCheckItem(allocUid(), it.done, it.text)) }
                }
            }
        }
    }
    val focusRequesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    var pendingFocusUid by remember { mutableStateOf<Long?>(null) }
    val savedDoneStates = remember { mutableMapOf<String, Boolean>() }

    LaunchedEffect(pendingFocusUid, items.size) {
        val target = pendingFocusUid ?: return@LaunchedEffect
        val requester = focusRequesters[target]
        if (requester != null) {
            runCatching { requester.requestFocus() }
            pendingFocusUid = null
        }
    }

    fun currentContent() = if (checklist) {
        serializeChecklist(
            items.map { CheckItem(it.done, it.cleanText.trimEnd()) }
                .filter { it.text.isNotBlank() }
        )
    } else {
        content.replace("\u200B", "")
    }
    fun close() {
        val c = currentContent()
        val changed = title != initial.title || c != initial.content || color != initial.colorIndex ||
            pinned != initial.pinned || hidden != initial.hidden || checklist != initial.isChecklist
        if ((title.isNotBlank() || c.isNotBlank()) && changed) {
            onSave(
                initial.copy(
                    title = title.trim(),
                    content = c,
                    colorIndex = color,
                    pinned = pinned,
                    hidden = hidden,
                    isChecklist = checklist,
                    updatedAt = System.currentTimeMillis()
                )
            )
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
                // Hide / Blur note toggle button
                IconButton(onClick = { hidden = !hidden }) {
                    Icon(
                        if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (hidden) "Unhide note" else "Hide note on Notes screen",
                        tint = if (hidden) t.accentStrong else fg.copy(alpha = 0.55f)
                    )
                }
                IconButton(onClick = {
                    if (!checklist) {
                        val parsed = content
                            .replace(IN_ITEM_NL, "\u0000")
                            .split("\n")
                            .map { it.replace("\u0000", "\n").replace("\u200B", "").trim() }
                            .filter { it.isNotBlank() }
                            .map { txt -> makeEditableCheckItem(allocUid(), savedDoneStates[txt] ?: false, txt) }
                        items.clear()
                        if (parsed.isEmpty()) {
                            val first = makeEditableCheckItem(allocUid(), false, "")
                            items.add(first)
                            pendingFocusUid = first.uid
                        } else {
                            items.addAll(parsed)
                        }
                    } else {
                        val allItems = items.map { CheckItem(it.done, it.cleanText.trim()) }.filter { it.text.isNotBlank() }
                        savedDoneStates.clear()
                        allItems.forEach { savedDoneStates[it.text] = it.done }
                        content = allItems.joinToString("\n") { it.text.replace("\n", IN_ITEM_NL) }
                    }
                    checklist = !checklist
                }) { Icon(if (checklist) Icons.AutoMirrored.Rounded.Notes else Icons.Rounded.Checklist, "Checklist", tint = fg) }
                IconButton(onClick = { pinned = !pinned }) { Icon(Icons.Rounded.PushPin, "Pin", tint = if (pinned) t.accentStrong else fg.copy(alpha = 0.45f)) }
                if (initial.id != 0L) {
                    ConfirmDeleteButton(onConfirmDelete = { onDelete(initial); onClose() }, iconTint = fg)
                }
                if (onConvertToTask != null) {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, "More options", tint = fg.copy(alpha = 0.45f))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            containerColor = t.card
                        ) {
                            DropdownMenuItem(
                                text = { Text("Turn into task", color = t.text) },
                                leadingIcon = { Icon(Icons.Rounded.TaskAlt, null, tint = t.accentStrong, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMoreMenu = false
                                    val checkListSnapshot = items.map { CheckItem(it.done, it.cleanText.trimEnd()) }.filter { it.text.isNotBlank() }
                                    val converted = convertNoteToTask(title, content, checklist, checkListSnapshot)
                                    close()
                                    onConvertToTask(converted)
                                }
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(visible = hidden) {
                Row(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(fg.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.VisibilityOff, null, tint = t.accentStrong, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Blurred on Notes screen & hidden from widget picker", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
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
                        key(it.uid) {
                            val requester = remember(it.uid) {
                                FocusRequester().also { r -> focusRequesters[it.uid] = r }
                            }
                            DisposableEffect(it.uid) {
                                onDispose { focusRequesters.remove(it.uid) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { items[i] = it.copy(done = !it.done) }, modifier = Modifier.size(36.dp)) {
                                    Icon(if (it.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = if (it.done) t.accentStrong else fg.copy(alpha = 0.5f))
                                }
                                BasicTextField(
                                    value = it.tfv,
                                    onValueChange = { v ->
                                        val targetUid = applyChecklistValueChange(items, i, v, ::allocUid)
                                        if (targetUid != null) {
                                            val immediate = focusRequesters[targetUid]
                                            if (immediate != null) {
                                                runCatching { immediate.requestFocus() }
                                            } else {
                                                pendingFocusUid = targetUid
                                            }
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = fg.copy(alpha = if (it.done) 0.4f else 0.9f),
                                        textDecoration = if (it.done) TextDecoration.LineThrough else null
                                    ),
                                    cursorBrush = SolidColor(t.accentStrong),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(requester)
                                        .onPreviewKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.Backspace &&
                                                it.tfv.selection.collapsed && it.tfv.selection.start <= 1
                                            ) {
                                                val targetUid = applyChecklistValueChange(
                                                    items,
                                                    i,
                                                    TextFieldValue(it.cleanText, TextRange(0)),
                                                    ::allocUid
                                                )
                                                if (targetUid != null) {
                                                    focusRequesters[targetUid]?.let { r -> runCatching { r.requestFocus() } }
                                                        ?: run { pendingFocusUid = targetUid }
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    decorationBox = { inner ->
                                        if (it.cleanText.isEmpty()) {
                                            Text("List item", style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.3f))
                                        }
                                        inner()
                                    }
                                )
                                IconButton(
                                    onClick = {
                                        if (items.size > 1) {
                                            items.removeAt(i)
                                        } else {
                                            items[0] = makeEditableCheckItem(it.uid, false, "")
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, null, tint = fg.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val created = makeEditableCheckItem(allocUid(), false, "")
                                items.add(created)
                                pendingFocusUid = created.uid
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = fg.copy(alpha = 0.5f))
                        }
                        Text("List item", style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.4f))
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
