package com.lokesh.tidy.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.lokesh.tidy.MainActivity
import com.lokesh.tidy.data.*
import com.lokesh.tidy.ui.Dates
import com.lokesh.tidy.ui.screens.isToday
import com.lokesh.tidy.ui.screens.parseChecklist
import com.lokesh.tidy.ui.screens.serializeChecklist
import com.lokesh.tidy.ui.theme.Palette
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val taskIdKey = ActionParameters.Key<Long>("task_id")
private val noteIdKey = ActionParameters.Key<Long>("note_id")
private val checkIndexKey = ActionParameters.Key<Int>("check_idx")
private val tickPrefKey = longPreferencesKey("tick")

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[taskIdKey] ?: return
        val repo = Repository.get(context)
        val t = repo.dao.task(id)?.task ?: return
        repo.setCompleted(id, !t.completed)
        WidgetUpdater.updateAll(context)
    }
}

class ToggleNoteCheckAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[noteIdKey] ?: return
        val idx = parameters[checkIndexKey] ?: return
        val repo = Repository.get(context)
        val note = repo.dao.note(noteId) ?: return
        if (!note.isChecklist) return
        val items = parseChecklist(note.content).toMutableList()
        if (idx !in items.indices) return
        items[idx] = items[idx].copy(done = !items[idx].done)
        repo.saveNote(note.copy(content = serializeChecklist(items), updatedAt = System.currentTimeMillis()))
        WidgetUpdater.updateAll(context)
    }
}

object WidgetUpdater {
    private suspend fun pushUpdate(ctx: Context, widget: GlanceAppWidget) {
        val mgr = GlanceAppWidgetManager(ctx)
        val ids = mgr.getGlanceIds(widget.javaClass)
        val now = System.nanoTime()
        for (gid in ids) {
            runCatching {
                updateAppWidgetState(ctx, gid) { prefs ->
                    prefs[tickPrefKey] = now
                }
                widget.update(ctx, gid)
            }
        }
    }

    suspend fun updateAll(ctx: Context) {
        WidgetPrefs.bump()
        pushUpdate(ctx, TodoWidget())
        pushUpdate(ctx, RingWidget())
        pushUpdate(ctx, StickyWidget())
        pushUpdate(ctx, CalendarWidget())
        pushUpdate(ctx, QuickWidget())
    }
}

private fun WidgetCfg.textColor(ctx: Context, isSticky: Boolean = false): ColorProvider {
    val style = WidgetArt.effectiveStyle(this, isSticky)
    val dark = isDark(ctx)
    return ColorProvider(
        if (style == -1 && !dark) Color(0xFF1C1B22)
        else if (dark) Color(0xFFF3F2F7)
        else Color(0xFF17171C)
    )
}

private fun WidgetCfg.mutedColor(ctx: Context, isSticky: Boolean = false): ColorProvider {
    val style = WidgetArt.effectiveStyle(this, isSticky)
    val dark = isDark(ctx)
    return ColorProvider(
        if (style == -1 && !dark) Color(0xFF56534C)
        else if (dark) Color(0xFF9D9DAA)
        else Color(0xFF6E6E7A)
    )
}

private fun WidgetCfg.accentColor() = ColorProvider(Palette.accents.getOrElse(accent) { Palette.accents[0] }.strong)
private fun WidgetCfg.fontFamily() = when (fontStyle) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> FontFamily.SansSerif }
private fun WidgetCfg.scale() = when (fontScale) { 0 -> 0.88f; 2 -> 1.15f; else -> 1f }

/**
 * Creates an Intent with a unique data URI so Android's PendingIntent cache never conflates
 * different widget actions (e.g., New Task vs New Note) or different task/note IDs.
 */
private fun appIntent(ctx: Context, action: String? = null, id: Long? = null, wid: Int = 0) =
    Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val act = action ?: "home"
        data = Uri.parse("tidy://widget/$act/${id ?: 0}/$wid")
        if (action != null) putExtra(MainActivity.EXTRA_ACTION, action)
        if (id != null) putExtra(MainActivity.EXTRA_ID, id)
    }

@Composable
private fun CardShell(
    ctx: Context,
    cfg: WidgetCfg,
    noteColor: Int? = null,
    isSticky: Boolean = false,
    onClick: Intent = appIntent(ctx),
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = ImageProvider(WidgetArt.cardBitmap(ctx, cfg, noteColorIdx = noteColor, isSticky = isSticky))
    Box(GlanceModifier.fillMaxSize().padding(2.dp)) {
        Image(
            provider = bg,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(onClick))
        )
        Column(GlanceModifier.fillMaxSize().padding(if (cfg.density == 1) 12.dp else 16.dp), content = content)
    }
}

// ---------- 1. To-Do List Widget ----------
class TodoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repo = Repository.get(context)
        val initCats = repo.dao.allCategories()
        val initTasks = repo.dao.allTasks()
        provideContent {
            val tick = currentState(tickPrefKey)
            val prefTick by WidgetPrefs.version.collectAsState()
            val cats by repo.categories.collectAsState(initial = initCats)
            val all by repo.tasks.collectAsState(initial = initTasks)
            val cfg = WidgetPrefs.load(context, wid)
            val today = LocalDate.now().toEpochDay()

            val filtered = all.filter {
                val t = it.task
                val matchList = when (cfg.listFilter) {
                    -1L -> true // All Tasks (Overdue + Today + Upcoming + Someday)
                    -2L -> t.dueDate != null && t.dueDate <= today // Today + Overdue
                    -3L -> t.dueDate == today // Today only
                    -4L -> t.dueDate == today || t.dueDate == null // Today + Someday
                    -5L -> t.dueDate != null && t.dueDate > today // Upcoming
                    -6L -> t.dueDate == null // Someday only
                    else -> t.categoryId == cfg.listFilter
                }
                matchList && (cfg.showCompleted || !t.completed)
            }.sortedWith(
                compareBy<TaskWithSubtasks> { it.task.completed }
                    .thenBy {
                        val d = it.task.dueDate
                        when {
                            d != null && d < today -> 0 // Overdue first
                            d == today -> 1             // Today second
                            d != null -> 2              // Upcoming third
                            else -> 3                   // Someday fourth
                        }
                    }
                    .thenBy { it.task.dueDate ?: Long.MAX_VALUE }
                    .thenBy { it.task.sortOrder }
            )

            val title = when (cfg.listFilter) {
                -1L -> "All Tasks"
                -2L -> "Today"
                -3L -> "Today Only"
                -4L -> "Today & Someday"
                -5L -> "Upcoming"
                -6L -> "Someday"
                else -> cats.find { it.id == cfg.listFilter }?.let { "${it.emoji} ${it.name}".trim() } ?: "Tasks"
            }
            val overdueCount = filtered.count { !it.task.completed && it.task.dueDate != null && it.task.dueDate < today }
            val remainingCount = filtered.count { !it.task.completed }
            val subtitleText = if (overdueCount > 0 && cfg.listFilter in listOf(-1L, -2L)) {
                "$remainingCount left · $overdueCount overdue"
            } else {
                "$remainingCount remaining"
            }

            val sc = cfg.scale(); val ff = cfg.fontFamily(); val dark = cfg.isDark(context)
            val overdueColor = ColorProvider(Color(0xFFFF5C6C))

            CardShell(context, cfg, onClick = appIntent(context, wid = wid)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(appIntent(context, wid = wid)))) {
                        Text(title, style = TextStyle(color = cfg.textColor(context), fontSize = (17 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                        Text(subtitleText, style = TextStyle(color = if (overdueCount > 0) overdueColor else cfg.mutedColor(context), fontSize = (11 * sc).sp, fontFamily = ff))
                    }
                    Image(
                        provider = ImageProvider(WidgetArt.fabBitmap(context, cfg.accent, 32)),
                        contentDescription = "Add",
                        modifier = GlanceModifier.size(32.dp).clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_NEW_TASK, wid = wid)))
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                if (filtered.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("All clear ✨", style = TextStyle(color = cfg.mutedColor(context), fontSize = (13 * sc).sp, fontFamily = ff))
                    }
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(filtered.take(30), itemId = { it.task.id }) { tw ->
                            val t = tw.task
                            val isOverdue = !t.completed && t.dueDate != null && t.dueDate < today
                            Row(
                                GlanceModifier.fillMaxWidth().padding(vertical = if (cfg.density == 1) 2.dp else 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Generous 32dp clickable target around the 22dp checkbox icon so taps register reliably
                                Box(
                                    GlanceModifier
                                        .size(32.dp)
                                        .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(taskIdKey to t.id))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(WidgetArt.checkIcon(context, t.completed, t.priority, cfg.accent, dark)),
                                        contentDescription = "Check",
                                        modifier = GlanceModifier.size(22.dp)
                                    )
                                }
                                Spacer(GlanceModifier.width(6.dp))
                                Column(
                                    GlanceModifier
                                        .defaultWeight()
                                        .padding(vertical = 2.dp)
                                        .clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_OPEN_TASK, t.id, wid)))
                                ) {
                                    Text(
                                        t.title, maxLines = 1,
                                        style = TextStyle(
                                            color = if (t.completed) cfg.mutedColor(context) else cfg.textColor(context),
                                            fontSize = (14 * sc).sp, fontWeight = FontWeight.Medium, fontFamily = ff,
                                            textDecoration = if (t.completed) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    )
                                    val sub = buildList {
                                        when {
                                            isOverdue -> add("Overdue · " + Dates.label(t.dueDate!!))
                                            t.dueDate == null && cfg.listFilter != -6L -> add("Someday")
                                            t.dueDate != null && t.dueDate != today && cfg.listFilter != -3L -> add(Dates.label(t.dueDate))
                                        }
                                        if (t.dueTime != null) add(Dates.time(t.dueTime))
                                        if (tw.subtasks.isNotEmpty()) add("${tw.subtasks.count { it.done }}/${tw.subtasks.size}")
                                    }.joinToString(" · ")
                                    if (sub.isNotEmpty() && (cfg.density == 0 || isOverdue)) {
                                        Text(
                                            sub,
                                            style = TextStyle(
                                                color = if (isOverdue) overdueColor else cfg.mutedColor(context),
                                                fontSize = (11 * sc).sp,
                                                fontFamily = ff
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 2. Today Progress Ring Widget ----------
class RingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repo = Repository.get(context)
        val initTasks = repo.dao.allTasks()
        provideContent {
            val tick = currentState(tickPrefKey)
            val prefTick by WidgetPrefs.version.collectAsState()
            val allTw by repo.tasks.collectAsState(initial = initTasks)
            val cfg = WidgetPrefs.load(context, wid)
            val all = allTw.map { it.task }
            val today = LocalDate.now().toEpochDay()
            val pool = all.filter { val d = it.dueDate; d != null && (d == today || (d < today && (!it.completed || isToday(it.completedAt)))) }
            val done = pool.count { it.completed }
            val streak = Repository.streaks(all).first
            val sc = cfg.scale(); val ff = cfg.fontFamily()
            CardShell(context, cfg, onClick = appIntent(context, wid = wid)) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(WidgetArt.ringBitmap(context, cfg, done, pool.size)), "Progress", GlanceModifier.size(78.dp))
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("TODAY", style = TextStyle(color = cfg.mutedColor(context), fontSize = (10 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                        Text("$done of ${pool.size}", style = TextStyle(color = cfg.textColor(context), fontSize = (17 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                        Text("done", style = TextStyle(color = cfg.mutedColor(context), fontSize = (12 * sc).sp, fontFamily = ff))
                        Spacer(GlanceModifier.height(4.dp))
                        Text("🔥 $streak d", style = TextStyle(color = cfg.accentColor(), fontSize = (12 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                    }
                }
            }
        }
    }
}

// ---------- 3. Sticky Note Widget ----------
class StickyWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repo = Repository.get(context)
        val initNotes = repo.dao.allNotes()
        provideContent {
            val tick = currentState(tickPrefKey)
            val prefTick by WidgetPrefs.version.collectAsState()
            val notes by repo.notes.collectAsState(initial = initNotes)
            val cfg = WidgetPrefs.load(context, wid)
            // If noteId == -1L ("Latest note"), pick the most recently updated note (not the oldest pinned note)
            val note = if (cfg.noteId != -1L) {
                notes.find { it.id == cfg.noteId } ?: notes.maxByOrNull { it.updatedAt }
            } else {
                notes.maxByOrNull { it.updatedAt }
            }

            val sc = cfg.scale(); val ff = cfg.fontFamily()
            val colorIdx = note?.colorIndex ?: 5
            val fg = cfg.textColor(context, isSticky = true)
            val fgMuted = cfg.mutedColor(context, isSticky = true)
            val dark = cfg.isDark(context)
            val clickIntent = if (note != null) {
                appIntent(context, MainActivity.ACTION_OPEN_NOTE, note.id, wid)
            } else {
                appIntent(context, MainActivity.ACTION_NEW_NOTE, wid = wid)
            }

            CardShell(context, cfg, noteColor = colorIdx, isSticky = true, onClick = clickIntent) {
                if (note == null) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tap to write a note 📝", style = TextStyle(color = fg, fontSize = (14 * sc).sp, fontFamily = ff))
                    }
                } else {
                    if (note.title.isNotBlank()) {
                        Text(
                            note.title,
                            maxLines = 2,
                            style = TextStyle(color = fg, fontSize = (16 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff),
                            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(clickIntent))
                        )
                        Spacer(GlanceModifier.height(6.dp))
                    }
                    if (note.isChecklist) {
                        val checkItems = parseChecklist(note.content)
                        LazyColumn(GlanceModifier.fillMaxSize()) {
                            items(checkItems.indices.toList(), itemId = { it.toLong() }) { idx ->
                                val item = checkItems[idx]
                                Row(
                                    GlanceModifier
                                        .fillMaxWidth()
                                        .padding(vertical = if (cfg.density == 1) 2.dp else 3.dp)
                                        .clickable(
                                            actionRunCallback<ToggleNoteCheckAction>(
                                                actionParametersOf(noteIdKey to note.id, checkIndexKey to idx)
                                            )
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        provider = ImageProvider(WidgetArt.checkIcon(context, item.done, 0, cfg.accent, dark)),
                                        contentDescription = "Checkbox",
                                        modifier = GlanceModifier.size(18.dp)
                                    )
                                    Spacer(GlanceModifier.width(8.dp))
                                    Text(
                                        item.text.replace("\n", " · "),
                                        maxLines = 2,
                                        style = TextStyle(
                                            color = if (item.done) fgMuted else fg,
                                            fontSize = (13 * sc).sp,
                                            fontFamily = ff,
                                            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            note.content,
                            maxLines = 10,
                            style = TextStyle(color = fgMuted, fontSize = (13 * sc).sp, fontFamily = ff),
                            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(clickIntent))
                        )
                    }
                }
            }
        }
    }
}

// ---------- 4. Calendar + Tasks Widget ----------
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repo = Repository.get(context)
        val initTasks = repo.dao.allTasks()
        provideContent {
            val tick = currentState(tickPrefKey)
            val prefTick by WidgetPrefs.version.collectAsState()
            val all by repo.tasks.collectAsState(initial = initTasks)
            val cfg = WidgetPrefs.load(context, wid)
            val today = LocalDate.now()
            val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val week = (0..6).map { start.plusDays(it.toLong()) }
            val todayTasks = all.filter { it.task.dueDate == today.toEpochDay() && (cfg.showCompleted || !it.task.completed) }
            val sc = cfg.scale(); val ff = cfg.fontFamily(); val dark = cfg.isDark(context)

            CardShell(context, cfg, onClick = appIntent(context, MainActivity.ACTION_CALENDAR, wid = wid)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + today.year,
                        style = TextStyle(color = cfg.textColor(context), fontSize = (15 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff),
                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_CALENDAR, wid = wid)))
                    )
                    Image(
                        ImageProvider(WidgetArt.fabBitmap(context, cfg.accent, 28)), "Add",
                        GlanceModifier.size(28.dp).clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_NEW_TASK, wid = wid)))
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val isToday = d == today
                        Column(GlanceModifier.defaultWeight().padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                style = TextStyle(color = if (isToday) cfg.accentColor() else cfg.mutedColor(context), fontSize = (10 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                            Text(d.dayOfMonth.toString(),
                                style = TextStyle(color = if (isToday) cfg.accentColor() else cfg.textColor(context), fontSize = (14 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                        }
                    }
                }
                Spacer(GlanceModifier.height(4.dp))
                if (todayTasks.isEmpty()) {
                    Text("No tasks scheduled today ✨", style = TextStyle(color = cfg.mutedColor(context), fontSize = (12 * sc).sp, fontFamily = ff))
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(todayTasks.take(12), itemId = { it.task.id }) { tw ->
                            val t = tw.task
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    GlanceModifier.size(28.dp).clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(taskIdKey to t.id))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(ImageProvider(WidgetArt.checkIcon(context, t.completed, t.priority, cfg.accent, dark)), null, GlanceModifier.size(20.dp))
                                }
                                Spacer(GlanceModifier.width(6.dp))
                                Text(
                                    t.title, maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_OPEN_TASK, t.id, wid))),
                                    style = TextStyle(color = if (t.completed) cfg.mutedColor(context) else cfg.textColor(context), fontSize = (13 * sc).sp, fontFamily = ff,
                                        textDecoration = if (t.completed) TextDecoration.LineThrough else TextDecoration.None)
                                )
                                if (t.dueTime != null) Text(Dates.time(t.dueTime), style = TextStyle(color = cfg.mutedColor(context), fontSize = (11 * sc).sp, fontFamily = ff))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 5. Quick Add 1×1 Widget ----------
class QuickWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wid = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val tick = currentState(tickPrefKey)
            val prefTick by WidgetPrefs.version.collectAsState()
            val cfg = WidgetPrefs.load(context, wid)
            val action = if (cfg.quickAction == 1) MainActivity.ACTION_NEW_NOTE else MainActivity.ACTION_NEW_TASK
            val targetIntent = appIntent(context, action, id = cfg.quickAction.toLong(), wid = wid)
            Box(
                GlanceModifier.fillMaxSize().clickable(actionStartActivity(targetIntent)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(WidgetArt.quickAddBitmap(context, cfg, 68)),
                    contentDescription = if (cfg.quickAction == 1) "New Note" else "New Task",
                    modifier = GlanceModifier.size(64.dp).clickable(actionStartActivity(targetIntent))
                )
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodoWidget() }
class RingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = RingWidget() }
class StickyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = StickyWidget() }
class CalendarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = CalendarWidget() }
class QuickWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = QuickWidget() }
