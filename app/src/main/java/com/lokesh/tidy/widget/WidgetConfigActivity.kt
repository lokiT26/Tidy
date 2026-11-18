package com.lokesh.tidy.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Note
import com.lokesh.tidy.data.Repository
import com.lokesh.tidy.ui.components.AmbientBackground
import com.lokesh.tidy.ui.components.SelectChip
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.TidyTheme
import com.lokesh.tidy.ui.theme.tidy
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val wid = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val reconfig = intent?.getBooleanExtra("reconfigure", false) == true
        if (!reconfig) setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val repo = Repository.get(this)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(wid)?.provider?.className ?: ""
        val kind = WidgetKind.entries.find { it.receiver.name == provider } ?: WidgetKind.TODO

        setContent {
            val s by repo.settings.flow.collectAsState(initial = com.lokesh.tidy.data.AppSettings())
            val cats by repo.categories.collectAsState(initial = emptyList())
            val notes by repo.notes.collectAsState(initial = emptyList())
            TidyTheme(s) {
                ConfigScreen(wid, kind, cats, notes, onSave = { cfg ->
                    WidgetPrefs.save(this, wid, cfg)
                    lifecycleScope.launch {
                        WidgetUpdater.updateAll(this@WidgetConfigActivity)
                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
                        finish()
                    }
                }, onCancel = { finish() })
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    wid: Int, kind: WidgetKind, categories: List<Category>, notes: List<Note>,
    onSave: (WidgetCfg) -> Unit, onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val t = tidy
    var cfg by remember { mutableStateOf(WidgetPrefs.load(ctx, wid)) }

    AmbientBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Live preview on simulated wallpaper
            Box(
                Modifier.fillMaxWidth().padding(18.dp).height(190.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF9183F4), Color(0xFFFFB499), Color(0xFF8FD9C4)))),
                contentAlignment = Alignment.Center
            ) {
                WidgetPreview(cfg, kind, notes)
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                Text("Customise ${kind.title}", style = MaterialTheme.typography.headlineSmall, color = t.text)
                Spacer(Modifier.height(14.dp))

                if (kind == WidgetKind.TODO) {
                    Section("Show tasks from") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { SelectChip("Today", cfg.listFilter == -2L) { cfg = cfg.copy(listFilter = -2L) } }
                            item { SelectChip("All", cfg.listFilter == -1L) { cfg = cfg.copy(listFilter = -1L) } }
                            items(categories) { c -> SelectChip("${c.emoji} ${c.name}".trim(), cfg.listFilter == c.id) { cfg = cfg.copy(listFilter = c.id) } }
                        }
                    }
                }
                if (kind == WidgetKind.STICKY) {
                    Section("Note to display") {
                        if (notes.isEmpty()) Text("No notes yet — your newest note will appear automatically.", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                        else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(notes) { n -> SelectChip(n.title.ifBlank { n.content.take(18).ifBlank { "Untitled" } }, cfg.noteId == n.id || (cfg.noteId == -1L && n == notes.first())) { cfg = cfg.copy(noteId = n.id) } }
                        }
                    }
                }
                if (kind == WidgetKind.QUICK) {
                    Section("Button action") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("+ New Task", cfg.quickAction == 0) { cfg = cfg.copy(quickAction = 0) }
                            SelectChip("✎ New Note", cfg.quickAction == 1) { cfg = cfg.copy(quickAction = 1) }
                        }
                    }
                }

                if (kind != WidgetKind.QUICK) {
                    Section("Background style") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Solid", "Gradient", "Frosted glass", "Pastel").forEachIndexed { i, l ->
                                item { SelectChip(l, cfg.bgStyle == i) { cfg = cfg.copy(bgStyle = i) } }
                            }
                        }
                    }
                    Section("Theme") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Follow system", "Light", "Dark", "AMOLED").forEachIndexed { i, l ->
                                item { SelectChip(l, cfg.theme == i) { cfg = cfg.copy(theme = i) } }
                            }
                        }
                    }
                    Section("Opacity · ${cfg.opacity}%") {
                        Slider(cfg.opacity.toFloat(), { cfg = cfg.copy(opacity = it.roundToInt()) }, valueRange = 20f..100f)
                    }
                    Section("Corner radius · ${cfg.radius} dp") {
                        Slider(cfg.radius.toFloat(), { cfg = cfg.copy(radius = it.roundToInt()) }, valueRange = 8f..38f)
                    }
                }

                Section("Accent colour") {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Palette.accents.forEachIndexed { i, a ->
                            val sel = cfg.accent == i
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(a.strong, a.partner)))
                                .border(if (sel) 3.dp else 0.dp, t.text, CircleShape).clickable { cfg = cfg.copy(accent = i) },
                                contentAlignment = Alignment.Center) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }

                if (kind != WidgetKind.QUICK) {
                    Section("Font size & style") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Small", "Medium", "Large").forEachIndexed { i, l -> SelectChip(l, cfg.fontScale == i) { cfg = cfg.copy(fontScale = i) } }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Modern", "Serif", "Mono").forEachIndexed { i, l -> SelectChip(l, cfg.fontStyle == i) { cfg = cfg.copy(fontStyle = i) } }
                        }
                    }
                    if (kind == WidgetKind.TODO || kind == WidgetKind.CALENDAR) {
                        Section("Density & completed") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SelectChip("Comfortable", cfg.density == 0) { cfg = cfg.copy(density = 0) }
                                SelectChip("Compact", cfg.density == 1) { cfg = cfg.copy(density = 1) }
                                SelectChip("Show done", cfg.showCompleted) { cfg = cfg.copy(showCompleted = !cfg.showCompleted) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(50)) { Text("Cancel") }
                Button({ onSave(cfg) }, Modifier.weight(2f).height(52.dp), shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = t.accentStrong)) { Text("Save widget", color = Color.White) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = tidy
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(22.dp)).background(t.card).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = t.textMuted)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun WidgetPreview(cfg: WidgetCfg, kind: WidgetKind, notes: List<Note>) {
    val ctx = LocalContext.current
    if (kind == WidgetKind.QUICK) {
        val bmp = remember(cfg) { WidgetArt.fabBitmap(ctx, cfg.accent, 68, if (cfg.quickAction == 1) "✎" else "+").asImageBitmap() }
        Image(bmp, null, Modifier.size(68.dp))
        return
    }
    val noteColor = if (kind == WidgetKind.STICKY) (notes.find { it.id == cfg.noteId } ?: notes.firstOrNull())?.colorIndex?.takeIf { it > 0 } ?: 5 else null
    val bmp = remember(cfg, noteColor) { WidgetArt.cardBitmap(ctx, cfg, 280f, 150f, noteColor).asImageBitmap() }
    val dark = cfg.isDark(ctx) && noteColor == null
    val fg = if (dark) Color(0xFFF3F2F7) else Color(0xFF1A1922)
    val ff = when (cfg.fontStyle) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val sc = when (cfg.fontScale) { 0 -> 0.88f; 2 -> 1.15f; else -> 1f }
    Box(Modifier.width(270.dp).height(148.dp)) {
        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(Modifier.fillMaxSize().padding(if (cfg.density == 1) 12.dp else 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(kind.title, color = fg, fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = (15 * sc).sp)
            Text("✓  Morning walk · 7:30 AM\n○  Design review · 2/4", color = fg.copy(alpha = 0.8f), fontFamily = ff, fontSize = (13 * sc).sp, lineHeight = (19 * sc).sp)
        }
    }
}
