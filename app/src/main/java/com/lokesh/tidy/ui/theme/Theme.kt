package com.lokesh.tidy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lokesh.tidy.R
import com.lokesh.tidy.data.AppSettings

@OptIn(ExperimentalTextApi::class)
private fun jakarta(w: Int) = Font(R.font.jakarta, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private val base = Typography()
val TidyType = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold),
    displayMedium = base.displayMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold),
    displaySmall = base.displaySmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

@Immutable
data class TidyColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val divider: Color,
    val glass: Color,
    val glassBorder: Color,
    val accent: Accent,
    val accentStrong: Color,
    val accentSoft: Color,
    val blobA: Color,
    val blobB: Color,
)

val LocalTidy = staticCompositionLocalOf { lightTidy(Palette.accents[0]) }

fun lightTidy(a: Accent) = TidyColors(
    dark = false,
    bg = Color(0xFFF7F5F2), card = Color.White, cardAlt = Color(0xFFF0EDE8),
    text = Color(0xFF17171C), textMuted = Color(0xFF6E6E7A), textFaint = Color(0xFFA9A9B4),
    divider = Color(0xFFEAE7E2), glass = Color.White.copy(alpha = 0.78f), glassBorder = Color.White,
    accent = a, accentStrong = a.strong, accentSoft = a.soft,
    blobA = a.pastel.copy(alpha = 0.55f), blobB = a.partner.copy(alpha = 0.40f),
)

fun darkTidy(a: Accent, amoled: Boolean) = TidyColors(
    dark = true,
    bg = if (amoled) Color.Black else Color(0xFF121214),
    card = if (amoled) Color(0xFF111114) else Color(0xFF1C1C21), cardAlt = if (amoled) Color(0xFF1A1A1F) else Color(0xFF26262C),
    text = Color(0xFFF3F2F7), textMuted = Color(0xFF9D9DAA), textFaint = Color(0xFF5E5E6A),
    divider = Color(0xFF2A2A31), glass = Color(0xFF26262D).copy(alpha = 0.82f), glassBorder = Color.White.copy(alpha = 0.10f),
    accent = a, accentStrong = lerp(a.strong, a.pastel, 0.35f), accentSoft = lerp(Color(0xFF1C1C21), a.strong, 0.22f),
    blobA = a.strong.copy(alpha = if (amoled) 0.16f else 0.22f), blobB = a.partner.copy(alpha = if (amoled) 0.08f else 0.12f),
)

@Composable
fun TidyTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val accent = Palette.accents.getOrElse(settings.accent) { Palette.accents[0] }
    val t = if (dark) darkTidy(accent, settings.amoled) else lightTidy(accent)
    val scheme = if (dark) darkColorScheme(
        primary = t.accentStrong, onPrimary = Color(0xFF121214), primaryContainer = t.accentSoft,
        secondary = accent.partner, background = t.bg, surface = t.card, onSurface = t.text, onBackground = t.text,
        surfaceVariant = t.cardAlt, onSurfaceVariant = t.textMuted, outline = t.divider, outlineVariant = t.divider,
        surfaceContainerHigh = t.card, surfaceContainer = t.card, surfaceContainerLow = t.card, surfaceContainerHighest = t.cardAlt,
    ) else lightColorScheme(
        primary = t.accentStrong, onPrimary = Color.White, primaryContainer = t.accentSoft,
        secondary = accent.partner, background = t.bg, surface = t.card, onSurface = t.text, onBackground = t.text,
        surfaceVariant = t.cardAlt, onSurfaceVariant = t.textMuted, outline = t.divider, outlineVariant = t.divider,
        surfaceContainerHigh = t.card, surfaceContainer = t.card, surfaceContainerLow = t.card, surfaceContainerHighest = t.cardAlt,
    )
    CompositionLocalProvider(LocalTidy provides t) {
        MaterialTheme(colorScheme = scheme, typography = TidyType, content = content)
    }
}

val tidy: TidyColors @Composable get() = LocalTidy.current
