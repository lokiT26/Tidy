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
import androidx.glance.action.Action
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
import com.lokesh.tidy.R
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
private val widgetIdKey = ActionParameters.Key<Int>("widget_id")
private val epochDayKey = ActionParameters.Key<Long>("epoch_day")
private val tickPrefKey = longPreferencesKey("tick")

/** Helper that attaches a click action WITHOUT Android/One UI's gray sharp-edged rectangle ripple. */
private fun GlanceModifier.noRippleClick(action: Action): GlanceModifier =
    this.clickable(onClick = action, rippleOverride = R.drawable.widget_no_ripple)

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

class SelectCalendarDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val wid = parameters[widgetIdKey] ?: GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val day = parameters[epochDayKey] ?: return
        WidgetPrefs.setSelectedDay(context, wid, day)
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
 * Creates an Intent with a unique data URI (including optional date, categoryId, or someday flags)
 * so Android's PendingIntent cache never conflates different widget actions or context defaults.
 */
private fun appIntent(
    ctx: Context,
    action: String? = null,
    id: Long? = null,
    wid: Int = 0,
    prefillDate: Long? = null,
    prefillCategory: Long? = null,
    prefillSomeday: Boolean = false,
) = Intent(ctx, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    val act = action ?: "home"
    val builder = Uri.parse("tidy://widget/$act/${id ?: 0}/$wid").buildUpon()
    if (prefillDate != null) builder.appendQueryParameter("date", prefillDate.toString())
    if (prefillCategory != null) builder.appendQueryParameter("cat", prefillCategory.toString())
    if (prefillSomeday) builder.appendQueryParameter("someday", "1")
    data = builder.build()
    if (action != null) putExtra(MainActivity.EXTRA_ACTION, action)
    if (id != null) putExtra(MainActivity.EXTRA_ID, id)
    if (prefillDate != null) putExtra(MainActivity.EXTRA_DATE, prefillDate)
    if (prefillCategory != null) putExtra(MainActivity.EXTRA_CATEGORY_ID, prefillCategory)
    if (prefillSomeday) putExtra(MainActivity.EXTRA_SOMEDAY, true)
}

@Composable
private fun CardShell(
    ctx: Context,
    cfg: WidgetCfg,
    wDp: Float = 320f,
    hDp: Float = 240f,
    noteColor: Int? = null,
    isSticky: Boolean = false,
    padH: Int? = null,
    padV: Int? = null,
    onClick: Intent = appIntent(ctx),
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = ImageProvider(WidgetArt.cardBitmap(ctx, cfg, wDp = wDp, hDp = hDp, noteColorIdx = noteColor, isSticky = isSticky))
    val defaultPad = if (cfg.density == 1) 12 else 16
    Box(GlanceModifier.fillMaxSize().padding(2.dp)) {
        Image(
            provider = bg,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize().noRippleClick(actionStartActivity(onClick))
        )
        Column(
            GlanceModifier
                .fillMaxSize()
                .padding(horizontal = (padH ?: defaultPad).dp, vertical = (padV ?: defaultPad).dp),
            content = content
        )
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
            val cfg = WidgetPrefs.load(context, wid, WidgetKind.TODO)
            val today = LocalDate.now().toEpochDay()

            val filtered = all.filter {
                val t = it.task
                val matchList = when (cfg.listFilter) {
                    -1L -> true
                    -2L -> t.dueDate != null && t.dueDate <= today
                    -3L -> t.dueDate == today
                    -4L -> t.dueDate == today || t.dueDate == null
                    -5L -> t.dueDate != null && t.dueDate > today
                    -6L -> t.dueDate == null
                    else -> t.categoryId == cfg.listFilter
                }
                matchList && (cfg.showCompleted || !t.completed)
            }.sortedWith(
                compareBy<TaskWithSubtasks> { it.task.completed }
                    .thenBy {
                        val d = it.task.dueDate
                        when {
                            d != null && d < today -> 0
                            d == today -> 1
                            d != null -> 2
                            else -> 3
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

            // Context-aware '+' and header intents (#8)
            val prefillCat = cfg.listFilter.takeIf { it >= 0L }
            val prefillDate = when (cfg.listFilter) {
                -2L, -3L -> today
                -5L -> today + 1
                else -> null
            }
            val prefillSomeday = cfg.listFilter == -6L
            val openListIntent = appIntent(
                context,
                action = null,
                wid = wid,
                prefillCategory = prefillCat
            )
            val newTaskIntent = appIntent(
                context,
                action = MainActivity.ACTION_NEW_TASK,
                wid = wid,
                prefillDate = prefillDate,
                prefillCategory = prefillCat,
                prefillSomeday = prefillSomeday
            )

            val sc = cfg.scale(); val ff = cfg.fontFamily(); val dark = cfg.isDark(context)
            val overdueColor = ColorProvider(Color(0xFFFF5C6C))

            CardShell(context, cfg, onClick = openListIntent) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().noRippleClick(actionStartActivity(openListIntent))) {
                        Text(title, style = TextStyle(color = cfg.textColor(context), fontSize = (17 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                        Text(subtitleText, style = TextStyle(color = if (overdueCount > 0) overdueColor else cfg.mutedColor(context), fontSize = (11 * sc).sp, fontFamily = ff))
                    }
                    Image(
                        provider = ImageProvider(WidgetArt.fabBitmap(context, cfg.accent, 32)),
                        contentDescription = "Add",
                        modifier = GlanceModifier.size(32.dp).noRippleClick(actionStartActivity(newTaskIntent))
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                if (filtered.isEmpty()) {
                    Box(
                        GlanceModifier.fillMaxSize().noRippleClick(actionStartActivity(openListIntent)),
                        contentAlignment = Alignment.Center
                    ) {
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
                                Box(
                                    GlanceModifier
                                        .size(32.dp)
                                        .noRippleClick(actionRunCallback<ToggleTaskAction>(actionParametersOf(taskIdKey to t.id))),
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
                                        .noRippleClick(actionStartActivity(appIntent(context, MainActivity.ACTION_OPEN_TASK, t.id, wid)))
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
                        // Clickable empty space below items so tapping empty space opens the app (#7)
                        item(itemId = -999L) {
                            Box(
                                GlanceModifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .noRippleClick(actionStartActivity(openListIntent))
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

// ---------- 2. Today Progress 2×1 Horizontal Widget (#9) ----------
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
            val cfg = WidgetPrefs.load(context, wid, WidgetKind.RING)
            val all = allTw.map { it.task }
            val today = LocalDate.now().toEpochDay()
            val pool = all.filter { val d = it.dueDate; d != null && (d == today || (d < today && (!it.completed || isToday(it.completedAt)))) }
            val done = pool.count { it.completed }
            val total = pool.size
            val streak = Repository.streaks(all).first
            val sc = cfg.scale(); val ff = cfg.fontFamily()
            val homeIntent = appIntent(context, wid = wid)

            CardShell(context, cfg, wDp = 240f, hDp = 82f, padH = 14, padV = 8, onClick = homeIntent) {
                Row(
                    GlanceModifier.fillMaxSize().noRippleClick(actionStartActivity(homeIntent)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(WidgetArt.ringBitmap(context, cfg, done, total, sizeDp = 54)),
                        contentDescription = "Progress",
                        modifier = GlanceModifier.size(52.dp)
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "TODAY",
                                style = TextStyle(color = cfg.mutedColor(context), fontSize = (10 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                "🔥 ${streak}d",
                                style = TextStyle(color = cfg.accentColor(), fontSize = (11 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff)
                            )
                        }
                        Text(
                            if (total == 0) "No tasks today" else if (done == total) "All $total done ✨" else "$done of $total done",
                            maxLines = 1,
                            style = TextStyle(color = cfg.textColor(context), fontSize = (14.5f * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff)
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Image(
                            provider = ImageProvider(WidgetArt.progressBarBitmap(context, cfg, done, total)),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = GlanceModifier.fillMaxWidth().height(5.dp)
                        )
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
            val cfg = WidgetPrefs.load(context, wid, WidgetKind.STICKY)
            // "Latest note (auto)" (-1L) automatically skips hidden/blurred notes (#1)
            val visibleNotes = notes.filter { !it.hidden }
            val note = if (cfg.noteId != -1L) {
                notes.find { it.id == cfg.noteId } ?: visibleNotes.maxByOrNull { it.updatedAt }
            } else {
                visibleNotes.maxByOrNull { it.updatedAt }
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
                    Box(
                        GlanceModifier.fillMaxSize().noRippleClick(actionStartActivity(clickIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tap to write a note 📝", style = TextStyle(color = fg, fontSize = (14 * sc).sp, fontFamily = ff))
                    }
                } else if (note.hidden) {
                    // Specifically pinned note was marked hidden/blurred in the app (#1)
                    Box(
                        GlanceModifier.fillMaxSize().noRippleClick(actionStartActivity(clickIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔒 Hidden note", style = TextStyle(color = fg, fontSize = (15 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                            Spacer(GlanceModifier.height(4.dp))
                            Text("Tap to view in Tidy", style = TextStyle(color = fgMuted, fontSize = (12 * sc).sp, fontFamily = ff))
                        }
                    }
                } else {
                    if (note.title.isNotBlank()) {
                        Text(
                            note.title,
                            maxLines = 2,
                            style = TextStyle(color = fg, fontSize = (16 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff),
                            modifier = GlanceModifier.fillMaxWidth().noRippleClick(actionStartActivity(clickIntent))
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
                                        .noRippleClick(
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
                            // Clickable empty space below checklist items opens the note in NoteEditor (#7)
                            item(itemId = -999L) {
                                Box(
                                    GlanceModifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .noRippleClick(actionStartActivity(clickIntent))
                                ) {}
                            }
                        }
                    } else {
                        Text(
                            note.content,
                            maxLines = 10,
                            style = TextStyle(color = fgMuted, fontSize = (13 * sc).sp, fontFamily = ff),
                            modifier = GlanceModifier.fillMaxSize().noRippleClick(actionStartActivity(clickIntent))
                        )
                    }
                }
            }
        }
    }
}

// ---------- 4. Calendar (Week + Tasks) Widget (#10) ----------
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
            val cfg = WidgetPrefs.load(context, wid, WidgetKind.CALENDAR)
            val today = LocalDate.now()
            val selectedDay = WidgetPrefs.getSelectedDay(context, wid)
            val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val week = (0..6).map { start.plusDays(it.toLong()) }
            val dayTasks = all.filter {
                it.task.dueDate == selectedDay.toEpochDay() && (cfg.showCompleted || !it.task.completed)
            }.sortedWith(compareBy<TaskWithSubtasks> { it.task.completed }.thenBy { it.task.dueTime ?: -1 }.thenBy { it.task.sortOrder })

            val sc = cfg.scale(); val ff = cfg.fontFamily(); val dark = cfg.isDark(context)
            val openCalIntent = appIntent(
                context,
                action = MainActivity.ACTION_CALENDAR,
                wid = wid,
                prefillDate = selectedDay.toEpochDay()
            )
            val newTaskOnDayIntent = appIntent(
                context,
                action = MainActivity.ACTION_NEW_TASK,
                wid = wid,
                prefillDate = selectedDay.toEpochDay()
            )
            val headerTitle = if (selectedDay == today) {
                today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + today.year
            } else {
                Dates.short(selectedDay)
            }

            CardShell(context, cfg, onClick = openCalIntent) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        headerTitle,
                        style = TextStyle(color = cfg.textColor(context), fontSize = (15 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff),
                        modifier = GlanceModifier.defaultWeight().noRippleClick(actionStartActivity(openCalIntent))
                    )
                    if (selectedDay != today) {
                        Text(
                            "Today",
                            style = TextStyle(color = cfg.accentColor(), fontSize = (11.5f * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff),
                            modifier = GlanceModifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .noRippleClick(
                                    actionRunCallback<SelectCalendarDayAction>(
                                        actionParametersOf(widgetIdKey to wid, epochDayKey to today.toEpochDay())
                                    )
                                )
                        )
                    }
                    Image(
                        ImageProvider(WidgetArt.fabBitmap(context, cfg.accent, 28)), "Add",
                        GlanceModifier.size(28.dp).noRippleClick(actionStartActivity(newTaskOnDayIntent))
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val isSelected = d == selectedDay
                        val isToday = d == today
                        val fgCol = when {
                            isSelected -> ColorProvider(Color.White)
                            isToday -> cfg.accentColor()
                            else -> cfg.textColor(context)
                        }
                        val subCol = when {
                            isSelected -> ColorProvider(Color.White.copy(alpha = 0.85f))
                            isToday -> cfg.accentColor()
                            else -> cfg.mutedColor(context)
                        }
                        Box(
                            GlanceModifier
                                .defaultWeight()
                                .height(42.dp)
                                .noRippleClick(
                                    actionRunCallback<SelectCalendarDayAction>(
                                        actionParametersOf(widgetIdKey to wid, epochDayKey to d.toEpochDay())
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected || isToday) {
                                Image(
                                    provider = ImageProvider(WidgetArt.dayPillBitmap(context, cfg.accent, isSelected, isToday, dark)),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = GlanceModifier.width(34.dp).height(42.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                    style = TextStyle(color = subCol, fontSize = (10 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff)
                                )
                                Text(
                                    d.dayOfMonth.toString(),
                                    style = TextStyle(color = fgCol, fontSize = (13.5f * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff)
                                )
                            }
                        }
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                if (dayTasks.isEmpty()) {
                    Box(
                        GlanceModifier.fillMaxSize().noRippleClick(actionStartActivity(openCalIntent)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            if (selectedDay == today) "No tasks scheduled today ✨" else "No tasks on ${Dates.label(selectedDay.toEpochDay())} ✨",
                            style = TextStyle(color = cfg.mutedColor(context), fontSize = (12 * sc).sp, fontFamily = ff)
                        )
                    }
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(dayTasks.take(12), itemId = { it.task.id }) { tw ->
                            val t = tw.task
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    GlanceModifier.size(28.dp).noRippleClick(actionRunCallback<ToggleTaskAction>(actionParametersOf(taskIdKey to t.id))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(ImageProvider(WidgetArt.checkIcon(context, t.completed, t.priority, cfg.accent, dark)), null, GlanceModifier.size(20.dp))
                                }
                                Spacer(GlanceModifier.width(6.dp))
                                Text(
                                    t.title, maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight().noRippleClick(actionStartActivity(appIntent(context, MainActivity.ACTION_OPEN_TASK, t.id, wid))),
                                    style = TextStyle(color = if (t.completed) cfg.mutedColor(context) else cfg.textColor(context), fontSize = (13 * sc).sp, fontFamily = ff,
                                        textDecoration = if (t.completed) TextDecoration.LineThrough else TextDecoration.None)
                                )
                                if (t.dueTime != null) Text(Dates.time(t.dueTime), style = TextStyle(color = cfg.mutedColor(context), fontSize = (11 * sc).sp, fontFamily = ff))
                            }
                        }
                        // Clickable empty space below items opens Calendar on the selected date (#7)
                        item(itemId = -999L) {
                            Box(
                                GlanceModifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .noRippleClick(actionStartActivity(openCalIntent))
                            ) {}
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
            val cfg = WidgetPrefs.load(context, wid, WidgetKind.QUICK)
            val action = if (cfg.quickAction == 1) MainActivity.ACTION_NEW_NOTE else MainActivity.ACTION_NEW_TASK
            val targetIntent = appIntent(context, action, id = cfg.quickAction.toLong(), wid = wid)
            Box(
                GlanceModifier.fillMaxSize().noRippleClick(actionStartActivity(targetIntent)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(WidgetArt.quickAddBitmap(context, cfg, 68)),
                    contentDescription = if (cfg.quickAction == 1) "New Note" else "New Task",
                    modifier = GlanceModifier.size(64.dp).noRippleClick(actionStartActivity(targetIntent))
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
