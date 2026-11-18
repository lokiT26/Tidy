package com.lokesh.tidy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.lokesh.tidy.MainActivity
import com.lokesh.tidy.data.*
import com.lokesh.tidy.ui.Dates
import com.lokesh.tidy.ui.screens.isToday
import com.lokesh.tidy.ui.screens.parseChecklist
import com.lokesh.tidy.ui.theme.Palette
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val taskIdKey = ActionParameters.Key<Long>("task_id")

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[taskIdKey] ?: return
        val repo = Repository.get(context)
        val t = repo.dao.task(id)?.task ?: return
        repo.setCompleted(id, !t.completed)
    }
}

object WidgetUpdater {
    suspend fun updateAll(ctx: Context) {
        TodoWidget().updateAll(ctx)
        RingWidget().updateAll(ctx)
        StickyWidget().updateAll(ctx)
        CalendarWidget().updateAll(ctx)
        QuickWidget().updateAll(ctx)
    }
}

private fun WidgetCfg.textColor(ctx: Context) = ColorProvider(if (isDark(ctx)) Color(0xFFF3F2F7) else Color(0xFF17171C))
private fun WidgetCfg.mutedColor(ctx: Context) = ColorProvider(if (isDark(ctx)) Color(0xFF9D9DAA) else Color(0xFF6E6E7A))
private fun WidgetCfg.accentColor() = ColorProvider(Palette.accents.getOrElse(accent) { Palette.accents[0] }.strong)
private fun WidgetCfg.fontFamily() = when (fontStyle) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> FontFamily.SansSerif }
private fun WidgetCfg.scale() = when (fontScale) { 0 -> 0.88f; 2 -> 1.15f; else -> 1f }

private fun appIntent(ctx: Context, action: String? = null, id: Long? = null) =
    Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (action != null) putExtra(MainActivity.EXTRA_ACTION, action)
        if (id != null) putExtra(MainActivity.EXTRA_ID, id)
    }

@Composable
private fun CardShell(ctx: Context, cfg: WidgetCfg, noteColor: Int? = null, onClick: Intent = appIntent(ctx), content: @Composable ColumnScope.() -> Unit) {
    val bg = ImageProvider(WidgetArt.cardBitmap(ctx, cfg, noteColorIdx = noteColor))
    Box(GlanceModifier.fillMaxSize().padding(2.dp)) {
        Image(provider = bg, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(onClick)))
        Column(GlanceModifier.fillMaxSize().padding(if (cfg.density == 1) 12.dp else 16.dp), content = content)
    }
}

// ---------- 1. To-Do List Widget ----------
class TodoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = WidgetPrefs.load(context, appWidgetId)
        val repo = Repository.get(context)
        val cats = repo.dao.allCategories()
        val all = repo.dao.allTasks()
        val today = LocalDate.now().toEpochDay()
        val filtered = all.filter {
            val t = it.task
            val matchList = when (cfg.listFilter) {
                -1L -> true
                -2L -> t.dueDate != null && t.dueDate <= today
                else -> t.categoryId == cfg.listFilter
            }
            matchList && (cfg.showCompleted || !t.completed)
        }
        val title = when (cfg.listFilter) {
            -1L -> "All Tasks"
            -2L -> "Today"
            else -> cats.find { it.id == cfg.listFilter }?.let { "${it.emoji} ${it.name}".trim() } ?: "Tasks"
        }
        provideContent {
            val sc = cfg.scale(); val ff = cfg.fontFamily(); val dark = cfg.isDark(context)
            CardShell(context, cfg) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(title, style = TextStyle(color = cfg.textColor(context), fontSize = (17 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                        Text("${filtered.count { !it.task.completed }} remaining", style = TextStyle(color = cfg.mutedColor(context), fontSize = (11 * sc).sp, fontFamily = ff))
                    }
                    Image(
                        provider = ImageProvider(WidgetArt.fabBitmap(context, cfg.accent, 32)),
                        contentDescription = "Add",
                        modifier = GlanceModifier.size(32.dp).clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_NEW_TASK)))
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("All clear ✨", style = TextStyle(color = cfg.mutedColor(context), fontSize = (13 * sc).sp, fontFamily = ff))
                    }
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(filtered.take(25), itemId = { it.task.id }) { tw ->
                            val t = tw.task
                            Row(
                                GlanceModifier.fillMaxWidth().padding(vertical = if (cfg.density == 1) 4.dp else 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    provider = ImageProvider(WidgetArt.checkIcon(context, t.completed, t.priority, cfg.accent, dark)),
                                    contentDescription = "Check",
                                    modifier = GlanceModifier.size(22.dp).clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(taskIdKey to t.id)))
                                )
                                Spacer(GlanceModifier.width(10.dp))
                                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_OPEN_TASK, t.id)))) {
                                    Text(
                                        t.title, maxLines = 1,
                                        style = TextStyle(
                                            color = if (t.completed) cfg.mutedColor(context) else cfg.textColor(context),
                                            fontSize = (14 * sc).sp, fontWeight = FontWeight.Medium, fontFamily = ff,
                                            textDecoration = if (t.completed) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    )
                                    val sub = buildList {
                                        if (t.dueTime != null) add(Dates.time(t.dueTime))
                                        else if (t.dueDate != null && cfg.listFilter != -2L) add(Dates.label(t.dueDate))
                                        if (tw.subtasks.isNotEmpty()) add("${tw.subtasks.count { it.done }}/${tw.subtasks.size}")
                                    }.joinToString(" · ")
                                    if (sub.isNotEmpty() && cfg.density == 0) {
                                        Text(sub, style = TextStyle(color = cfg.mutedColor(context), fontSize = (11 * sc).sp, fontFamily = ff), maxLines = 1)
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
        val cfg = WidgetPrefs.load(context, wid)
        val all = Repository.get(context).dao.allTasks().map { it.task }
        val today = LocalDate.now().toEpochDay()
        val pool = all.filter { val d = it.dueDate; d != null && (d == today || (d < today && (!it.completed || isToday(it.completedAt)))) }
        val done = pool.count { it.completed }
        val streak = Repository.streaks(all).first
        provideContent {
            val sc = cfg.scale(); val ff = cfg.fontFamily()
            CardShell(context, cfg) {
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
        val cfg = WidgetPrefs.load(context, wid)
        val notes = Repository.get(context).dao.allNotes()
        val note = notes.find { it.id == cfg.noteId } ?: notes.firstOrNull()
        provideContent {
            val sc = cfg.scale(); val ff = cfg.fontFamily()
            val colorIdx = note?.colorIndex?.takeIf { it > 0 } ?: 5
            val fg = ColorProvider(if (cfg.isDark(context)) Color(0xFFF3F2F7) else Color(0xFF1C1B22))
            val fgMuted = ColorProvider(if (cfg.isDark(context)) Color(0xFFB5B4C0) else Color(0xFF56534C))
            CardShell(context, cfg, noteColor = colorIdx, onClick = if (note != null) appIntent(context, MainActivity.ACTION_OPEN_NOTE, note.id) else appIntent(context, MainActivity.ACTION_NEW_NOTE)) {
                if (note == null) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tap to write a note 📝", style = TextStyle(color = fg, fontSize = (14 * sc).sp, fontFamily = ff))
                    }
                } else {
                    if (note.title.isNotBlank()) {
                        Text(note.title, maxLines = 2, style = TextStyle(color = fg, fontSize = (16 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                        Spacer(GlanceModifier.height(6.dp))
                    }
                    val body = if (note.isChecklist) parseChecklist(note.content).joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.text } else note.content
                    Text(body, maxLines = 8, style = TextStyle(color = fgMuted, fontSize = (13 * sc).sp, fontFamily = ff))
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
        val cfg = WidgetPrefs.load(context, wid)
        val all = Repository.get(context).dao.allTasks()
        val today = LocalDate.now()
        val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val week = (0..6).map { start.plusDays(it.toLong()) }
        val todayTasks = all.filter { it.task.dueDate == today.toEpochDay() && (cfg.showCompleted || !it.task.completed) }
        provideContent {
            val sc = cfg.scale(); val ff = cfg.fontFamily(); val dark = cfg.isDark(context)
            CardShell(context, cfg, onClick = appIntent(context, MainActivity.ACTION_CALENDAR)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + today.year,
                        style = TextStyle(color = cfg.textColor(context), fontSize = (15 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff),
                        modifier = GlanceModifier.defaultWeight())
                    Image(ImageProvider(WidgetArt.fabBitmap(context, cfg.accent, 28)), "Add",
                        GlanceModifier.size(28.dp).clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_NEW_TASK))))
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val isToday = d == today
                        Column(GlanceModifier.defaultWeight().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                style = TextStyle(color = if (isToday) cfg.accentColor() else cfg.mutedColor(context), fontSize = (10 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                            Text(d.dayOfMonth.toString(),
                                style = TextStyle(color = if (isToday) cfg.accentColor() else cfg.textColor(context), fontSize = (14 * sc).sp, fontWeight = FontWeight.Bold, fontFamily = ff))
                        }
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                if (todayTasks.isEmpty()) {
                    Text("No tasks scheduled today ✨", style = TextStyle(color = cfg.mutedColor(context), fontSize = (12 * sc).sp, fontFamily = ff))
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(todayTasks.take(10), itemId = { it.task.id }) { tw ->
                            val t = tw.task
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(ImageProvider(WidgetArt.checkIcon(context, t.completed, t.priority, cfg.accent, dark)), null,
                                    GlanceModifier.size(20.dp).clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(taskIdKey to t.id))))
                                Spacer(GlanceModifier.width(8.dp))
                                Text(t.title, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                                    style = TextStyle(color = if (t.completed) cfg.mutedColor(context) else cfg.textColor(context), fontSize = (13 * sc).sp, fontFamily = ff,
                                        textDecoration = if (t.completed) TextDecoration.LineThrough else TextDecoration.None))
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
        val cfg = WidgetPrefs.load(context, wid)
        val action = if (cfg.quickAction == 1) MainActivity.ACTION_NEW_NOTE else MainActivity.ACTION_NEW_TASK
        provideContent {
            Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(appIntent(context, action))), contentAlignment = Alignment.Center) {
                Image(ImageProvider(WidgetArt.fabBitmap(context, cfg.accent, 64, if (cfg.quickAction == 1) "✎" else "+")), "Add", GlanceModifier.size(60.dp))
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodoWidget() }
class RingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = RingWidget() }
class StickyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = StickyWidget() }
class CalendarWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = CalendarWidget() }
class QuickWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = QuickWidget() }
