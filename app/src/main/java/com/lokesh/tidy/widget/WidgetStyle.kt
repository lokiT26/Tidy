package com.lokesh.tidy.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import com.lokesh.tidy.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import kotlin.math.roundToInt

enum class WidgetKind(val title: String, val subtitle: String, val receiver: Class<*>) {
    TODO("To-Do List", "Check off tasks from your home screen", TodoWidgetReceiver::class.java),
    RING("Today Progress", "2×1 progress ring + daily streak", RingWidgetReceiver::class.java),
    STICKY("Sticky Note", "Pin a note to your home screen", StickyWidgetReceiver::class.java),
    CALENDAR("Week + Tasks", "Interactive 7-day strip with day's tasks", CalendarWidgetReceiver::class.java),
    QUICK("Quick Add", "1×1 button to add a task or note", QuickWidgetReceiver::class.java),
}

data class WidgetCfg(
    /**
     * For non-Sticky widgets: 0 = Solid, 1 = Gradient, 2 = Frosted glass, 3 = Pastel
     * For Sticky widget: 0 = Note colour, 1 = Solid, 2 = Gradient, 3 = Frosted glass, 4 = Pastel
     */
    val bgStyle: Int = 0,
    /** 20..100 */
    val opacity: Int = 92,
    /** 8..38 dp */
    val radius: Int = 26,
    /** 0 = system, 1 = light, 2 = dark, 3 = amoled */
    val theme: Int = 0,
    /** Index into Palette.accents */
    val accent: Int = 0,
    /** 0 = small, 1 = medium, 2 = large */
    val fontScale: Int = 1,
    /** 0 = modern (sans), 1 = serif, 2 = mono */
    val fontStyle: Int = 0,
    /** 0 = comfortable, 1 = compact */
    val density: Int = 0,
    val showCompleted: Boolean = false,
    /**
     * -1 = All Tasks (Overdue + Today + Upcoming + Someday)
     * -2 = Today + Overdue
     * -3 = Today only
     * -4 = Today + Someday
     * -5 = Upcoming
     * -6 = Someday only
     * >=0 = specific categoryId
     */
    val listFilter: Long = -2L,
    /** For sticky widget: -1L = Latest note (auto), >0 = specific noteId */
    val noteId: Long = -1L,
    /** For quick widget: 0 = new task, 1 = new note */
    val quickAction: Int = 0,
) {
    fun isDark(ctx: Context): Boolean = when (theme) {
        1 -> false
        2, 3 -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}

object WidgetPrefs {
    private val _version = MutableStateFlow(System.nanoTime())
    val version: StateFlow<Long> = _version

    fun bump() {
        _version.value = System.nanoTime()
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("widget_cfg", Context.MODE_PRIVATE)

    fun load(ctx: Context, id: Int, kind: WidgetKind? = null): WidgetCfg {
        val p = sp(ctx)
        if (!p.contains("op_$id")) {
            return if (kind == WidgetKind.QUICK) WidgetCfg(bgStyle = 1, opacity = 100, radius = 28) else WidgetCfg()
        }
        return WidgetCfg(
            bgStyle = p.getInt("bg_$id", if (kind == WidgetKind.QUICK) 1 else 0),
            opacity = p.getInt("op_$id", 92),
            radius = p.getInt("rad_$id", 26),
            theme = p.getInt("th_$id", 0),
            accent = p.getInt("ac_$id", 0),
            fontScale = p.getInt("fs_$id", 1),
            fontStyle = p.getInt("ff_$id", 0),
            density = p.getInt("dn_$id", 0),
            showCompleted = p.getBoolean("sc_$id", false),
            listFilter = p.getLong("lf_$id", -2L),
            noteId = p.getLong("nt_$id", -1L),
            quickAction = p.getInt("qa_$id", 0),
        )
    }

    fun save(ctx: Context, id: Int, c: WidgetCfg) {
        sp(ctx).edit()
            .putInt("bg_$id", c.bgStyle)
            .putInt("op_$id", c.opacity)
            .putInt("rad_$id", c.radius)
            .putInt("th_$id", c.theme)
            .putInt("ac_$id", c.accent)
            .putInt("fs_$id", c.fontScale)
            .putInt("ff_$id", c.fontStyle)
            .putInt("dn_$id", c.density)
            .putBoolean("sc_$id", c.showCompleted)
            .putLong("lf_$id", c.listFilter)
            .putLong("nt_$id", c.noteId)
            .putInt("qa_$id", c.quickAction)
            .commit()
        bump()
    }

    /** Selected epoch day for Week + Tasks widget (defaults to today within current week). */
    fun getSelectedDay(ctx: Context, wid: Int): LocalDate {
        val today = LocalDate.now()
        val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val end = start.plusDays(6)
        val raw = sp(ctx).getLong("cal_day_$wid", today.toEpochDay())
        val d = LocalDate.ofEpochDay(raw)
        return if (d.isBefore(start) || d.isAfter(end)) today else d
    }

    fun setSelectedDay(ctx: Context, wid: Int, epochDay: Long) {
        sp(ctx).edit().putLong("cal_day_$wid", epochDay).commit()
        bump()
    }
}

/**
 * Renders widget backgrounds and custom graphics as bitmaps so arbitrary corner radius,
 * gradients, glass highlights and opacity sliders work identically on Android 11 / One UI Core 3.1.
 */
object WidgetArt {
    private fun Int.withAlpha(a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).roundToInt(), Color.red(this), Color.green(this), Color.blue(this))
    private fun androidx.compose.ui.graphics.Color.argb(): Int = Color.argb((alpha * 255).roundToInt(), (red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())

    /**
     * Resolves the effective style index (0=Solid, 1=Gradient, 2=Glass, 3=Pastel, -1=NoteColor)
     */
    fun effectiveStyle(cfg: WidgetCfg, isSticky: Boolean): Int {
        return if (isSticky) {
            when (cfg.bgStyle) {
                0 -> -1 // Note colour
                1 -> 0  // Solid
                2 -> 1  // Gradient
                3 -> 2  // Frosted glass
                else -> 3 // Pastel
            }
        } else {
            cfg.bgStyle.coerceIn(0, 3)
        }
    }

    fun cardBitmap(ctx: Context, cfg: WidgetCfg, wDp: Float = 320f, hDp: Float = 240f, noteColorIdx: Int? = null, isSticky: Boolean = false): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = (wDp * dm.density).roundToInt().coerceIn(80, 900)
        val h = (hDp * dm.density).roundToInt().coerceIn(60, 900)
        val r = (cfg.radius * dm.density).coerceAtMost(minOf(w, h) / 2f - 1f)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
        val dark = cfg.isDark(ctx)
        val a = Palette.accents.getOrElse(cfg.accent) { Palette.accents[0] }
        val alpha = (cfg.opacity.coerceIn(20, 100)) / 100f
        val style = effectiveStyle(cfg, isSticky)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (style) {
            -1 -> { // Note colour (adapts to Light / Dark / AMOLED theme + opacity)
                val idx = (noteColorIdx ?: 0).coerceIn(0, Palette.pastels.lastIndex)
                val base = when {
                    cfg.theme == 3 && idx == 0 -> Color.BLACK
                    cfg.theme == 3 -> Palette.noteColor(idx, true).argb()
                    idx == 0 && !dark -> 0xFFFFF8E1.toInt()
                    else -> Palette.noteColor(idx, dark).argb()
                }
                p.color = base.withAlpha(alpha)
                c.drawRoundRect(rect, r, r, p)
            }
            0 -> { // Solid
                val base = when { cfg.theme == 3 -> Color.BLACK; dark -> 0xFF1C1C22.toInt(); else -> Color.WHITE }
                p.color = base.withAlpha(alpha)
                c.drawRoundRect(rect, r, r, p)
            }
            1 -> { // Gradient
                val c1 = (if (dark) a.strong else a.pastel).argb().withAlpha(alpha)
                val c2 = (if (cfg.theme == 3) Color.BLACK else if (dark) 0xFF1E192A.toInt() else a.partner.argb()).withAlpha(alpha)
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c1, c2, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
            }
            2 -> { // Frosted glass
                val base = (if (cfg.theme == 3) 0xFF0A0A0E.toInt() else if (dark) 0xFF22222B.toInt() else Color.WHITE).withAlpha((alpha * 0.78f).coerceAtMost(0.92f))
                p.color = base
                c.drawRoundRect(rect, r, r, p)
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a.pastel.argb().withAlpha(0.28f * alpha), a.partner.argb().withAlpha(0.20f * alpha), Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
                p.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.WHITE.withAlpha(if (dark) 0.12f else 0.55f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
            }
            else -> { // Pastel
                val base = (if (cfg.theme == 3) 0xFF12101B.toInt() else if (dark) 0xFF252030.toInt() else a.soft.argb()).withAlpha(alpha)
                p.color = base
                c.drawRoundRect(rect, r, r, p)
                if (dark) {
                    p.color = a.strong.argb().withAlpha(0.18f * alpha)
                    c.drawRoundRect(rect, r, r, p)
                }
            }
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.2f * dm.density
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.WHITE.withAlpha(if (dark) 0.18f else 0.75f),
                (if (dark) Color.WHITE else 0xFFD8D4CE.toInt()).withAlpha(if (dark) 0.05f else 0.35f),
                Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(rect, r, r, stroke)
        return bmp
    }

    fun ringBitmap(ctx: Context, cfg: WidgetCfg, done: Int, total: Int, sizeDp: Int = 54): Bitmap {
        val size = (sizeDp * ctx.resources.displayMetrics.density).roundToInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dark = cfg.isDark(ctx)
        val a = Palette.accents.getOrElse(cfg.accent) { Palette.accents[0] }
        val stroke = size * 0.125f
        val pad = stroke / 2 + 2.5f
        val rect = RectF(pad, pad, size - pad, size - pad)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
            color = if (dark) 0x28FFFFFF else 0x1C000000
        }
        c.drawArc(rect, 0f, 360f, false, track)
        val ratio = if (total == 0) 0f else done / total.toFloat()
        if (ratio > 0f) {
            val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
                shader = SweepGradient(size / 2f, size / 2f, intArrayOf(a.strong.argb(), a.partner.argb(), a.strong.argb()), null).apply {
                    val m = Matrix(); m.preRotate(-90f, size / 2f, size / 2f); setLocalMatrix(m)
                }
            }
            c.drawArc(rect, -90f, 360f * ratio.coerceIn(0f, 1f), false, fg)
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size * 0.25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (dark) Color.WHITE else 0xFF1A1922.toInt()
        }
        val pct = "${(ratio * 100).roundToInt()}%"
        c.drawText(pct, size / 2f, size / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        return bmp
    }

    /** Horizontal progress bar bitmap for the redesigned 2x1 Today Progress widget. */
    fun progressBarBitmap(ctx: Context, cfg: WidgetCfg, done: Int, total: Int): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = (120 * dm.density).roundToInt()
        val h = (6 * dm.density).roundToInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dark = cfg.isDark(ctx)
        val a = Palette.accents.getOrElse(cfg.accent) { Palette.accents[0] }
        val r = h / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = if (dark) 0x26FFFFFF else 0x1A000000
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, p)
        val ratio = if (total == 0) 0f else (done / total.toFloat()).coerceIn(0f, 1f)
        if (ratio > 0f) {
            val fillW = (w * ratio).coerceAtLeast(h.toFloat())
            p.shader = LinearGradient(0f, 0f, fillW, 0f, a.strong.argb(), a.partner.argb(), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(0f, 0f, fillW, h.toFloat()), r, r, p)
        }
        return bmp
    }

    /** Selected/today pill background for a day column in the Week + Tasks widget. */
    fun dayPillBitmap(ctx: Context, accentIdx: Int, selected: Boolean, isToday: Boolean, dark: Boolean): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = (34 * dm.density).roundToInt()
        val h = (44 * dm.density).roundToInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val a = Palette.accents.getOrElse(accentIdx) { Palette.accents[0] }
        val r = 12 * dm.density
        val rect = RectF(1f, 1f, w - 1f, h - 1f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (selected) {
            p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a.strong.argb(), a.partner.argb(), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, r, r, p)
        } else if (isToday) {
            p.color = a.strong.argb().withAlpha(if (dark) 0.24f else 0.16f)
            c.drawRoundRect(rect, r, r, p)
        }
        return bmp
    }

    fun checkIcon(ctx: Context, checked: Boolean, priority: Int, accentIdx: Int, dark: Boolean): Bitmap {
        val s = (24 * ctx.resources.displayMetrics.density).roundToInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val a = Palette.accents.getOrElse(accentIdx) { Palette.accents[0] }
        val col = if (priority in 1..3) Palette.priority[priority].argb() else a.strong.argb()
        val r = s / 2f - 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (checked) {
            p.color = a.strong.argb()
            c.drawCircle(s / 2f, s / 2f, r, p)
            p.color = Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.11f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
            val path = Path().apply { moveTo(s * 0.28f, s * 0.52f); lineTo(s * 0.44f, s * 0.68f); lineTo(s * 0.74f, s * 0.34f) }
            c.drawPath(path, p)
        } else {
            p.color = col; p.alpha = 38; c.drawCircle(s / 2f, s / 2f, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.09f; p.alpha = if (priority == 0) 150 else 235
            if (priority == 0) p.color = if (dark) 0xFFA2A2B0.toInt() else 0xFF787886.toInt()
            c.drawCircle(s / 2f, s / 2f, r - 1f, p)
        }
        return bmp
    }

    fun fabBitmap(ctx: Context, accentIdx: Int, sizeDp: Int = 56, symbol: String = "+", opacity: Int = 100): Bitmap {
        val s = (sizeDp * ctx.resources.displayMetrics.density).roundToInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val a = Palette.accents.getOrElse(accentIdx) { Palette.accents[0] }
        val alpha = (opacity.coerceIn(20, 100)) / 100f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), a.strong.argb().withAlpha(alpha), a.partner.argb().withAlpha(alpha), Shader.TileMode.CLAMP)
        }
        c.drawCircle(s / 2f, s / 2f, s / 2f - 1f, p)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE.withAlpha((alpha + 0.15f).coerceAtMost(1f))
            textAlign = Paint.Align.CENTER; textSize = s * 0.52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        c.drawText(symbol, s / 2f, s / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        return bmp
    }

    /**
     * 1x1 Quick Add bitmap supporting Background style (Solid, Gradient, Frosted glass, Pastel),
     * Theme (System, Light, Dark, AMOLED), Accent colour, Opacity, Shape radius, and a clean '+'
     * icon with a "TASK" or "NOTE" label below.
     */
    fun quickAddBitmap(ctx: Context, cfg: WidgetCfg, sizeDp: Int = 72): Bitmap {
        val dm = ctx.resources.displayMetrics
        val s = (sizeDp * dm.density).roundToInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val a = Palette.accents.getOrElse(cfg.accent) { Palette.accents[0] }
        val dark = cfg.isDark(ctx)
        val alpha = (cfg.opacity.coerceIn(20, 100)) / 100f
        val r = (cfg.radius.coerceIn(14, 36) / 36f) * (s / 2f - 2f)
        val rect = RectF(2f, 2f, s - 2f, s - 2f)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (cfg.bgStyle.coerceIn(0, 3)) {
            0 -> { // Solid
                val base = when { cfg.theme == 3 -> Color.BLACK; dark -> 0xFF1C1C22.toInt(); else -> Color.WHITE }
                p.color = base.withAlpha(alpha)
                c.drawRoundRect(rect, r, r, p)
            }
            1 -> { // Gradient
                val c1 = a.strong.argb().withAlpha(alpha)
                val c2 = (if (cfg.theme == 3) 0xFF1A162B.toInt() else a.partner.argb()).withAlpha(alpha)
                p.shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), c1, c2, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
            }
            2 -> { // Frosted glass
                val base = (if (cfg.theme == 3) 0xFF0A0A0E.toInt() else if (dark) 0xFF22222B.toInt() else Color.WHITE).withAlpha((alpha * 0.78f).coerceAtMost(0.92f))
                p.color = base
                c.drawRoundRect(rect, r, r, p)
                p.shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), a.pastel.argb().withAlpha(0.32f * alpha), a.partner.argb().withAlpha(0.24f * alpha), Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
            }
            else -> { // Pastel
                val base = (if (cfg.theme == 3) 0xFF12101B.toInt() else if (dark) 0xFF252030.toInt() else a.soft.argb()).withAlpha(alpha)
                p.color = base
                c.drawRoundRect(rect, r, r, p)
            }
        }

        // Subtle inner highlight border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.2f * dm.density
            color = if (cfg.bgStyle == 1) Color.WHITE.withAlpha(0.42f * alpha) else a.strong.argb().withAlpha(0.35f * alpha)
        }
        c.drawRoundRect(rect, r, r, borderPaint)

        // Foreground color adapts to background style & theme
        val fgColor = when {
            cfg.bgStyle == 1 -> Color.WHITE
            dark -> a.pastel.argb()
            else -> a.strong.argb()
        }
        val fgAlpha = (alpha + 0.18f).coerceAtMost(1f)

        // Unified clean '+' icon for both Task and Note
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fgColor.withAlpha(fgAlpha)
            this.style = Paint.Style.STROKE
            strokeWidth = s * 0.068f
            strokeCap = Paint.Cap.ROUND
        }
        val cx = s * 0.5f
        val cy = s * 0.38f
        val len = s * 0.145f
        c.drawLine(cx - len, cy, cx + len, cy, iconPaint)
        c.drawLine(cx, cy - len, cx, cy + len, iconPaint)

        // Bottom label ("TASK" or "NOTE")
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (if (cfg.bgStyle == 1) Color.WHITE else if (dark) 0xFFF3F2F7.toInt() else 0xFF17171C.toInt()).withAlpha(fgAlpha)
            textAlign = Paint.Align.CENTER
            textSize = s * 0.17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        val label = if (cfg.quickAction == 1) "NOTE" else "TASK"
        c.drawText(label, s / 2f, s * 0.80f, labelPaint)
        return bmp
    }
}
