package com.lokesh.tidy.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import com.lokesh.tidy.R
import com.lokesh.tidy.ui.theme.Palette
import kotlin.math.roundToInt

enum class WidgetKind(val title: String, val subtitle: String, val receiver: Class<*>) {
    TODO("To-Do List", "Check off tasks from your home screen", TodoWidgetReceiver::class.java),
    RING("Today Progress", "Progress ring + streak", RingWidgetReceiver::class.java),
    STICKY("Sticky Note", "Pin a note to your home screen", StickyWidgetReceiver::class.java),
    CALENDAR("Week + Tasks", "7-day strip with today's list", CalendarWidgetReceiver::class.java),
    QUICK("Quick Add", "1×1 button to add a task or note", QuickWidgetReceiver::class.java),
}

data class WidgetCfg(
    /** 0 = solid, 1 = gradient, 2 = frosted glass, 3 = pastel card */
    val bgStyle: Int = 0,
    /** 0..100 */
    val opacity: Int = 92,
    /** 12..36 dp */
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
    /** -1 = all, -2 = today only, >=0 = categoryId */
    val listFilter: Long = -2L,
    /** For sticky widget */
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
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widget_cfg", Context.MODE_PRIVATE)

    fun load(ctx: Context, id: Int): WidgetCfg {
        val p = sp(ctx)
        if (!p.contains("op_$id")) return WidgetCfg()
        return WidgetCfg(
            bgStyle = p.getInt("bg_$id", 0),
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
            .apply()
    }
}

/**
 * Renders widget backgrounds and custom graphics as bitmaps so arbitrary corner radius,
 * gradients, glass highlights and opacity sliders work identically on Android 11 / One UI Core 3.1.
 */
object WidgetArt {
    private fun Int.withAlpha(a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).roundToInt(), Color.red(this), Color.green(this), Color.blue(this))
    private fun androidx.compose.ui.graphics.Color.argb(): Int = Color.argb((alpha * 255).roundToInt(), (red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())

    fun cardBitmap(ctx: Context, cfg: WidgetCfg, wDp: Float = 320f, hDp: Float = 240f, noteColorIdx: Int? = null): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = (wDp * dm.density).roundToInt().coerceIn(160, 900)
        val h = (hDp * dm.density).roundToInt().coerceIn(160, 900)
        val r = cfg.radius * dm.density
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
        val dark = cfg.isDark(ctx)
        val a = Palette.accents.getOrElse(cfg.accent) { Palette.accents[0] }
        val alpha = cfg.opacity / 100f

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (noteColorIdx != null && noteColorIdx > 0) {
            val base = Palette.noteColor(noteColorIdx, dark).argb()
            p.color = base.withAlpha(alpha)
            c.drawRoundRect(rect, r, r, p)
        } else when (cfg.bgStyle) {
            0 -> { // Solid
                val base = when { cfg.theme == 3 -> Color.BLACK; dark -> 0xFF1C1C22.toInt(); else -> Color.WHITE }
                p.color = base.withAlpha(alpha)
                c.drawRoundRect(rect, r, r, p)
            }
            1 -> { // Gradient
                val c1 = (if (dark) a.strong else a.pastel).argb().withAlpha(alpha)
                val c2 = (if (dark) 0xFF1E192A.toInt() else a.partner.argb()).withAlpha(alpha)
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c1, c2, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
            }
            2 -> { // Frosted glass
                val base = (if (dark) 0xFF22222B.toInt() else Color.WHITE).withAlpha((alpha * 0.78f).coerceAtMost(0.92f))
                p.color = base
                c.drawRoundRect(rect, r, r, p)
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a.pastel.argb().withAlpha(0.25f * alpha), a.partner.argb().withAlpha(0.18f * alpha), Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
                p.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.WHITE.withAlpha(if (dark) 0.10f else 0.55f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, p)
            }
            else -> { // Pastel
                val base = (if (dark) 0xFF252030.toInt() else a.soft.argb()).withAlpha(alpha)
                p.color = base
                c.drawRoundRect(rect, r, r, p)
            }
        }
        // Subtle border
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * dm.density
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.WHITE.withAlpha(if (dark) 0.16f else 0.75f),
                (if (dark) Color.WHITE else 0xFFD8D4CE.toInt()).withAlpha(if (dark) 0.04f else 0.35f),
                Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(rect, r, r, stroke)
        return bmp
    }

    fun ringBitmap(ctx: Context, cfg: WidgetCfg, done: Int, total: Int): Bitmap {
        val size = (110 * ctx.resources.displayMetrics.density).roundToInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dark = cfg.isDark(ctx) || cfg.bgStyle == 1 && cfg.isDark(ctx)
        val a = Palette.accents.getOrElse(cfg.accent) { Palette.accents[0] }
        val stroke = size * 0.11f
        val pad = stroke / 2 + 4f
        val rect = RectF(pad, pad, size - pad, size - pad)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
            color = if (dark) 0x22FFFFFF else 0x18000000
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
            textSize = size * 0.23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (dark) Color.WHITE else 0xFF1A1922.toInt()
        }
        val pct = "${(ratio * 100).roundToInt()}%"
        c.drawText(pct, size / 2f, size / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
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
            p.color = Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.1f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
            val path = Path().apply { moveTo(s * 0.28f, s * 0.52f); lineTo(s * 0.44f, s * 0.68f); lineTo(s * 0.74f, s * 0.34f) }
            c.drawPath(path, p)
        } else {
            p.color = col; p.alpha = 35; c.drawCircle(s / 2f, s / 2f, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.085f; p.alpha = if (priority == 0) 120 else 230
            if (priority == 0) p.color = if (dark) 0xFF8E8E9A.toInt() else 0xFF9A9AA6.toInt()
            c.drawCircle(s / 2f, s / 2f, r - 1f, p)
        }
        return bmp
    }

    fun fabBitmap(ctx: Context, accentIdx: Int, sizeDp: Int = 56, symbol: String = "+"): Bitmap {
        val s = (sizeDp * ctx.resources.displayMetrics.density).roundToInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val a = Palette.accents.getOrElse(accentIdx) { Palette.accents[0] }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), a.strong.argb(), a.partner.argb(), Shader.TileMode.CLAMP)
        }
        c.drawCircle(s / 2f, s / 2f, s / 2f - 1f, p)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = s * 0.52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        c.drawText(symbol, s / 2f, s / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        return bmp
    }
}
