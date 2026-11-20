package com.lokesh.tidy.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lokesh.tidy.data.AppSettings
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.reminder.Reminders
import com.lokesh.tidy.ui.AppViewModel
import com.lokesh.tidy.ui.components.IconBubble
import com.lokesh.tidy.ui.theme.Palette
import com.lokesh.tidy.ui.theme.tidy
import com.lokesh.tidy.widget.WidgetConfigActivity
import com.lokesh.tidy.widget.WidgetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: AppViewModel, s: AppSettings, categories: List<Category>, onEditCategory: (Category?) -> Unit, onBack: () -> Unit) {
    val t = tidy
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    var name by remember(s.name) { mutableStateOf(s.name) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { val json = vm.repo.exportJson(); ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } } }.isSuccess
            Toast.makeText(ctx, if (ok) "Backup saved ✓" else "Backup failed", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { val json = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }; vm.repo.importJson(json) } }.isSuccess
            Toast.makeText(ctx, if (ok) "Restored ✓" else "Couldn't read that file", Toast.LENGTH_SHORT).show()
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 60.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, { vm.setName(name.trim().take(30)); onBack() })
            Spacer(Modifier.width(14.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = t.text)
        }
        Spacer(Modifier.height(20.dp))

        Group("Profile") {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(t.accentStrong, t.accent.partner))), contentAlignment = Alignment.Center) {
                    Text(name.firstOrNull()?.uppercase() ?: "🙂", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your name", style = MaterialTheme.typography.labelMedium, color = t.textMuted, modifier = Modifier.weight(1f))
                        Text("${name.length}/30", style = MaterialTheme.typography.labelSmall, color = if (name.length >= 30) Palette.danger else t.textFaint)
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { if (it.length <= 30) { name = it; vm.setName(it.trim()) } },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = t.text),
                        cursorBrush = SolidColor(t.accentStrong),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> if (name.isEmpty()) Text("Tap to add", style = MaterialTheme.typography.titleMedium, color = t.textFaint); inner() }
                    )
                }
            }
        }

        Group("Appearance") {
            Column(Modifier.padding(16.dp)) {
                Text("Theme", style = MaterialTheme.typography.labelMedium, color = t.textMuted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.cardAlt).padding(4.dp)) {
                    listOf("System" to Icons.Rounded.Contrast, "Light" to Icons.Rounded.LightMode, "Dark" to Icons.Rounded.DarkMode).forEachIndexed { i, (label, icon) ->
                        val sel = s.themeMode == i
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) t.card else Color.Transparent).clickable { vm.setTheme(i) }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = if (sel) t.accentStrong else t.textMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) t.text else t.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Accent", style = MaterialTheme.typography.labelMedium, color = t.textMuted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Palette.accents.forEachIndexed { i, a ->
                        val sel = s.accent == i
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(a.strong, a.partner)))
                            .border(if (sel) 3.dp else 0.dp, if (sel) t.text else Color.Transparent, CircleShape).clickable { vm.setAccent(i) },
                            contentAlignment = Alignment.Center) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                Text(Palette.accents.getOrElse(s.accent) { Palette.accents[0] }.name, style = MaterialTheme.typography.labelSmall, color = t.textFaint, modifier = Modifier.padding(top = 8.dp))
            }
            Toggle(Icons.Rounded.Brightness2, "Pure black (AMOLED)", "Deeper blacks in dark mode", s.amoled) { vm.setAmoled(it) }
            Toggle(Icons.Rounded.Celebration, "Confetti", "Celebrate when you finish the day", s.confetti) { vm.setConfetti(it) }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    if (s.separatePinnedNoteSize) "Note box height · Others" else "Note box height",
                    style = MaterialTheme.typography.labelMedium,
                    color = t.textMuted
                )
                Spacer(Modifier.height(8.dp))
                NoteHeightSelector(selected = s.noteSizeMode, onSelect = { vm.setNoteSizeMode(it) })
                AnimatedVisibility(visible = s.separatePinnedNoteSize) {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("Note box height · Pinned", style = MaterialTheme.typography.labelMedium, color = t.textMuted)
                        Spacer(Modifier.height(8.dp))
                        NoteHeightSelector(selected = s.pinnedNoteSizeMode, onSelect = { vm.setPinnedNoteSizeMode(it) })
                    }
                }
            }
            Toggle(
                icon = Icons.Rounded.PushPin,
                title = "Different height for Pinned",
                subtitle = "Set a separate box size for pinned notes",
                value = s.separatePinnedNoteSize
            ) { vm.setSeparatePinnedNoteSize(it) }
        }

        Group("Lists") {
            categories.forEach { c ->
                Item(null, "${c.emoji}  ${c.name}", null, dot = Palette.vivid.getOrElse(c.colorIndex) { Palette.vivid[0] }) { onEditCategory(c) }
            }
            Item(Icons.Rounded.Add, "New list", null) { onEditCategory(null) }
        }

        Group("Home-screen widgets") {
            val mgr = AppWidgetManager.getInstance(ctx)
            val placed = WidgetKind.entries.flatMap { k -> mgr.getAppWidgetIds(ComponentName(ctx, k.receiver)).map { k to it } }
            if (placed.isEmpty()) Item(Icons.Rounded.Widgets, "No widgets placed yet", "Long-press home screen → Widgets → Tidy") {}
            placed.forEach { (k, id) ->
                Item(Icons.Rounded.Widgets, "${k.title}  #$id", "Tap to customise") {
                    ctx.startActivity(Intent(ctx, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("reconfigure", true))
                }
            }
        }

        Group("Notifications") {
            val lifecycleOwner = LocalLifecycleOwner.current
            var batteryUnrestricted by remember {
                mutableStateOf(ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true)
            }
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, ev ->
                    if (ev == Lifecycle.Event.ON_RESUME) {
                        batteryUnrestricted = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }

            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                Item(Icons.Rounded.NotificationsActive, "Allow notifications", "Needed for reminders") { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            if (!batteryUnrestricted) {
                Item(Icons.Rounded.BatteryChargingFull, "Unrestricted background", "Recommended so Samsung doesn't delay reminders") {
                    runCatching {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }.onFailure {
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")))
                    }
                }
            }
            Item(Icons.Rounded.NotificationAdd, "Send test reminder (5s)", "Lock your phone to verify alerts fire on time") {
                Reminders.scheduleTest(ctx)
                Toast.makeText(ctx, "Test reminder scheduled in 5 seconds", Toast.LENGTH_SHORT).show()
            }
            Item(Icons.Rounded.Settings, "Notification settings", "Sound, vibration, pop-up style") {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        }

        Group("Backup") {
            Item(Icons.Rounded.CloudUpload, "Export backup", "Save everything to a .json file") { exportLauncher.launch("tidy-backup-${LocalDate.now()}.json") }
            Item(Icons.Rounded.CloudDownload, "Restore backup", "Replaces current data") { importLauncher.launch(arrayOf("application/json", "*/*")) }
        }

        val pkgVersion = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.2" }
        Text("Tidy $pkgVersion · made for Lokesh · 100% offline", style = MaterialTheme.typography.labelSmall, color = t.textFaint, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun NoteHeightSelector(selected: Int, onSelect: (Int) -> Unit) {
    val t = tidy
    val options = listOf("Auto", "Small", "Medium", "Large")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.cardAlt).padding(4.dp)) {
        options.forEachIndexed { idx, label ->
            val sel = selected == idx
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) t.card else Color.Transparent)
                    .clickable { onSelect(idx) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) t.accentStrong else t.textMuted)
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = tidy
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = t.textMuted, modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.card), content = content)
}

@Composable
private fun Item(icon: ImageVector?, title: String, subtitle: String?, dot: Color? = null, onClick: () -> Unit) {
    val t = tidy
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(t.accentSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = t.accentStrong, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(14.dp)) }
        if (dot != null) { Box(Modifier.size(10.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = t.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = t.textFaint)
    }
}

@Composable
private fun Toggle(icon: ImageVector, title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val t = tidy
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(t.accentSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = t.accentStrong, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = t.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = t.accentStrong, uncheckedTrackColor = t.cardAlt, uncheckedBorderColor = t.divider))
    }
}
