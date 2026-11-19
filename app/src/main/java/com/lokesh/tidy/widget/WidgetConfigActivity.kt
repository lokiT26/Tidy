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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Note
import com.lokesh.tidy.data.Repository
import com.lokesh.tidy.ui.components.AmbientBackground
import com.lokesh.tidy.ui.components.SelectChip
import com.lokesh.tidy.ui.screens.parseChecklist
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
                Modifier.fillMaxWidth().padding(18.dp).height(196.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF9183F4), Color(0xFFFFB499), Color(0xFF8FD9C4)))),
                contentAlignment = Alignment.Center
            ) {
                WidgetPreview(cfg, kind, notes, categories)
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                Text("Customise ${kind.title}", style = MaterialTheme.typography.headlineSmall, color = t.text)
                Spacer(Modifier.height(14.dp))

                if (kind == WidgetKind.TODO) {
                    Section("Show tasks from") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { SelectChip("Today + Overdue", cfg.listFilter == -2L) { cfg = cfg.copy(listFilter = -2L) } }
                            item { SelectChip("Today only", cfg.listFilter == -3L) { cfg = cfg.copy(listFilter = -3L) } }
                            item { SelectChip("Today + Someday", cfg.listFilter == -4L) { cfg = cfg.copy(listFilter = -4L) } }
                            item { SelectChip("Upcoming", cfg.listFilter == -5L) { cfg = cfg.copy(listFilter = -5L) } }
                            item { SelectChip("Someday", cfg.listFilter == -6L) { cfg = cfg.copy(listFilter = -6L) } }
                            item { SelectChip("All Tasks", cfg.listFilter == -1L) { cfg = cfg.copy(listFilter = -1L) } }
                            items(categories) { c -> SelectChip("${c.emoji} ${c.name}".trim(), cfg.listFilter == c.id) { cfg = cfg.copy(listFilter = c.id) } }
                        }
                    }
                }
                if (kind == WidgetKind.STICKY) {
                    Section("Note to display") {
                        if (notes.isEmpty()) {
                            Text("No notes yet — your newest note will appear automatically.", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    SelectChip("✨ Latest note (auto)", cfg.noteId == -1L) { cfg = cfg.copy(noteId = -1L) }
                                }
                                items(notes) { n ->
                                    val label = n.title.ifBlank { n.content.replace("\u200B", "").lines().firstOrNull()?.removePrefix("[ ] ")?.removePrefix("[x] ")?.take(18) ?: "Untitled" }.ifBlank { "Untitled" }
                                    SelectChip(label, cfg.noteId == n.id) { cfg = cfg.copy(noteId = n.id) }
                                }
                            }
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
                    val bgStyles = if (kind == WidgetKind.STICKY) {
                        listOf("Note colour", "Solid", "Gradient", "Frosted glass", "Pastel")
                    } else {
                        listOf("Solid", "Gradient", "Frosted glass", "Pastel")
                    }
                    Section("Background style") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            bgStyles.forEachIndexed { i, l ->
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
                }

                // Opacity & Corner Radius available on ALL widgets (including Quick Add!)
                Section("Opacity · ${cfg.opacity}%") {
                    Slider(cfg.opacity.toFloat(), { cfg = cfg.copy(opacity = it.roundToInt()) }, valueRange = 20f..100f)
                }
                Section(if (kind == WidgetKind.QUICK) "Shape radius · ${cfg.radius} dp" else "Corner radius · ${cfg.radius} dp") {
                    Slider(
                        value = cfg.radius.toFloat(),
                        onValueChange = { cfg = cfg.copy(radius = it.roundToInt()) },
                        valueRange = if (kind == WidgetKind.QUICK) 14f..36f else 8f..38f
                    )
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
                    if (kind == WidgetKind.TODO || kind == WidgetKind.CALENDAR || kind == WidgetKind.STICKY) {
                        Section(if (kind == WidgetKind.STICKY) "Density" else "Density & completed") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SelectChip("Comfortable", cfg.density == 0) { cfg = cfg.copy(density = 0) }
                                SelectChip("Compact", cfg.density == 1) { cfg = cfg.copy(density = 1) }
                                if (kind != WidgetKind.STICKY) {
                                    SelectChip("Show done", cfg.showCompleted) { cfg = cfg.copy(showCompleted = !cfg.showCompleted) }
                                }
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
private fun WidgetPreview(cfg: WidgetCfg, kind: WidgetKind, notes: List<Note>, categories: List<Category>) {
    val ctx = LocalContext.current
    if (kind == WidgetKind.QUICK) {
        val bmp = remember(cfg) { WidgetArt.quickAddBitmap(ctx, cfg, 76).asImageBitmap() }
        Image(bmp, null, Modifier.size(76.dp))
        return
    }
    val isSticky = kind == WidgetKind.STICKY
    val selectedNote = remember(notes, cfg.noteId) {
        if (!isSticky) null
        else if (cfg.noteId != -1L) notes.find { it.id == cfg.noteId } ?: notes.maxByOrNull { it.updatedAt }
        else notes.maxByOrNull { it.updatedAt }
    }
    val noteColor = if (isSticky) (selectedNote?.colorIndex ?: 5) else null
    val bmp = remember(cfg, noteColor, isSticky) { WidgetArt.cardBitmap(ctx, cfg, 280f, 155f, noteColor, isSticky).asImageBitmap() }
    val dark = cfg.isDark(ctx)
    val effStyle = WidgetArt.effectiveStyle(cfg, isSticky)
    val fg = if (effStyle == -1 && !dark) Color(0xFF1C1B22) else if (dark) Color(0xFFF3F2F7) else Color(0xFF1A1922)
    val fgMuted = fg.copy(alpha = 0.68f)
    val accentColor = Palette.accents.getOrElse(cfg.accent) { Palette.accents[0] }.strong
    val ff = when (cfg.fontStyle) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val sc = when (cfg.fontScale) { 0 -> 0.88f; 2 -> 1.15f; else -> 1f }

    Box(Modifier.width(276.dp).height(154.dp)) {
        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        when (kind) {
            WidgetKind.RING -> {
                val ringBmp = remember(cfg) { WidgetArt.ringBitmap(ctx, cfg, 5, 8).asImageBitmap() }
                Row(
                    Modifier.fillMaxSize().padding(if (cfg.density == 1) 12.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(ringBmp, null, Modifier.size(82.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("TODAY", color = fgMuted, fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = (10 * sc).sp)
                        Text("5 of 8", color = fg, fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = (17 * sc).sp)
                        Text("done", color = fgMuted, fontFamily = ff, fontSize = (12 * sc).sp)
                        Spacer(Modifier.height(4.dp))
                        Text("🔥 6 d", color = accentColor, fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = (12 * sc).sp)
                    }
                }
            }
            WidgetKind.STICKY -> {
                val checkDoneBmp = remember(cfg, dark) { WidgetArt.checkIcon(ctx, true, 0, cfg.accent, dark).asImageBitmap() }
                val checkOpenBmp = remember(cfg, dark) { WidgetArt.checkIcon(ctx, false, 0, cfg.accent, dark).asImageBitmap() }
                Column(Modifier.fillMaxSize().padding(if (cfg.density == 1) 12.dp else 16.dp)) {
                    val title = selectedNote?.title?.ifBlank { "Sticky Note" } ?: "Sticky Note"
                    Text(title, color = fg, fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = (15 * sc).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    if (selectedNote?.isChecklist == true) {
                        val items = parseChecklist(selectedNote.content).take(3)
                        items.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Image(if (item.done) checkDoneBmp else checkOpenBmp, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    item.text.replace("\n", " · "),
                                    color = if (item.done) fgMuted else fg,
                                    fontFamily = ff,
                                    fontSize = (12.5f * sc).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (item.done) TextDecoration.LineThrough else null
                                )
                            }
                        }
                    } else {
                        val body = selectedNote?.content?.ifBlank { "Your note preview appears here…" } ?: "Notes live here — pick a colour, pin the important ones."
                        Text(body, color = fgMuted, fontFamily = ff, fontSize = (12.5f * sc).sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            else -> {
                val checkDoneBmp = remember(cfg, dark) { WidgetArt.checkIcon(ctx, true, 0, cfg.accent, dark).asImageBitmap() }
                val checkOpenBmp = remember(cfg, dark) { WidgetArt.checkIcon(ctx, false, 2, cfg.accent, dark).asImageBitmap() }
                val fabBmp = remember(cfg) { WidgetArt.fabBitmap(ctx, cfg.accent, 28).asImageBitmap() }
                val headerTitle = if (kind == WidgetKind.CALENDAR) "Week + Tasks" else when (cfg.listFilter) {
                    -1L -> "All Tasks"
                    -2L -> "Today"
                    -3L -> "Today Only"
                    -4L -> "Today & Someday"
                    -5L -> "Upcoming"
                    -6L -> "Someday"
                    else -> categories.find { it.id == cfg.listFilter }?.let { "${it.emoji} ${it.name}".trim() } ?: "Tasks"
                }
                Column(Modifier.fillMaxSize().padding(if (cfg.density == 1) 12.dp else 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(headerTitle, color = fg, fontFamily = ff, fontWeight = FontWeight.Bold, fontSize = (15 * sc).sp, modifier = Modifier.weight(1f))
                        Image(fabBmp, null, Modifier.size(26.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(checkOpenBmp, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Design review · 2/4", color = fg, fontFamily = ff, fontSize = (13 * sc).sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(checkDoneBmp, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Morning walk · 7:30 AM",
                                color = fgMuted,
                                fontFamily = ff,
                                fontSize = (13 * sc).sp,
                                textDecoration = if (cfg.showCompleted) TextDecoration.LineThrough else null
                            )
                        }
                    }
                }
            }
        }
    }
}
