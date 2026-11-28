package com.lokesh.tidy.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lokesh.tidy.data.AiPlanDraft
import com.lokesh.tidy.data.AiUndoSummary
import com.lokesh.tidy.data.AppSettings
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Repository
import com.lokesh.tidy.reminder.Reminders
import com.lokesh.tidy.ui.AppViewModel
import com.lokesh.tidy.ui.Dates
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
    var showAiImportSheet by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }

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
            val lastUndo = remember(s.lastAiImportBatch) { AiUndoSummary.fromJson(s.lastAiImportBatch) }
            Item(
                Icons.Rounded.AutoAwesome,
                "Import AI plan",
                if (lastUndo != null) "Add tasks & notes from any AI · Last: ${lastUndo.label()}"
                else "Add tasks & notes generated by any AI"
            ) { showAiImportSheet = true }
            Item(Icons.Rounded.CloudUpload, "Export backup", "Save everything to a .json file") { exportLauncher.launch("tidy-backup-${LocalDate.now()}.json") }
            Item(Icons.Rounded.CloudDownload, "Restore backup", "Replaces current data") { importLauncher.launch(arrayOf("application/json", "*/*")) }
        }

        val pkgVersion = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.7.2" }
        val footerTextColor = t.textMuted.copy(alpha = 0.80f)
        val separatorDotColor = t.accentStrong.copy(alpha = if (t.dark) 0.90f else 0.82f)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tidy $pkgVersion",
                style = MaterialTheme.typography.labelSmall,
                color = footerTextColor
            )
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .size(3.5.dp)
                    .clip(CircleShape)
                    .background(separatorDotColor)
            )
            Text(
                "100% offline",
                style = MaterialTheme.typography.labelSmall,
                color = footerTextColor
            )
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .size(3.5.dp)
                    .clip(CircleShape)
                    .background(separatorDotColor)
            )
            Text(
                text = "Contact & feedback",
                style = MaterialTheme.typography.labelSmall,
                color = footerTextColor,
                modifier = Modifier
                    .clickable { showContactDialog = true }
                    .padding(bottom = 3.dp)
                    .drawBehind {
                        val y = size.height + 1.5.dp.toPx()
                        drawLine(
                            color = footerTextColor.copy(alpha = 0.72f),
                            start = Offset(1.dp.toPx(), y),
                            end = Offset(size.width - 1.dp.toPx(), y),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.3.dp.toPx(), 3.6.dp.toPx()), 0f)
                        )
                    }
            )
        }
    }

    if (showAiImportSheet) {
        AiPlanImportSheet(
            vm = vm,
            s = s,
            categories = categories,
            initialDraft = null,
            onDismiss = { showAiImportSheet = false }
        )
    }
    if (showContactDialog) {
        val pkgVersion = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.7.2" }
        ContactFeedbackDialog(
            pkgVersion = pkgVersion,
            onDismiss = { showContactDialog = false }
        )
    }
}

@Composable
private fun ContactFeedbackDialog(
    pkgVersion: String,
    onDismiss: () -> Unit,
) {
    val t = tidy
    val ctx = LocalContext.current
    val deviceInfo = remember(pkgVersion) {
        "Tidy $pkgVersion · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
    }
    fun openUrl(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(ctx, "Couldn't open browser", Toast.LENGTH_SHORT).show()
        }
    }

    val bugColor = if (t.dark) Color(0xFFFF6B6B) else Color(0xFFE5484D)
    val ideaColor = if (t.dark) Color(0xFFFFA94D) else Color(0xFFE67700)
    val starColor = if (t.dark) Color(0xFFFFD43B) else Color(0xFFD99B00)
    val releaseColor = if (t.dark) Color(0xFF3DD68C) else Color(0xFF189A5E)
    val shareColor = if (t.dark) Color(0xFF66A8FF) else Color(0xFF1C7ED6)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.card,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text("Contact & feedback", style = MaterialTheme.typography.titleLarge, color = t.text)
                Spacer(Modifier.height(2.dp))
                Text("Tidy v$pkgVersion · 100% offline", style = MaterialTheme.typography.labelSmall, color = t.accentStrong)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactOptionRow(
                    icon = Icons.Rounded.BugReport,
                    iconColor = bugColor,
                    title = "Report a bug",
                    subtitle = "Found something broken or unexpected?"
                ) {
                    onDismiss()
                    val encDevice = Uri.encode(deviceInfo)
                    openUrl("https://github.com/lokiT26/Tidy/issues/new?template=bug.yml&title=%5BBug%5D+&device=$encDevice")
                }
                ContactOptionRow(
                    icon = Icons.Rounded.Lightbulb,
                    iconColor = ideaColor,
                    title = "Suggest an idea",
                    subtitle = "Share a feature request or feedback"
                ) {
                    onDismiss()
                    openUrl("https://github.com/lokiT26/Tidy/issues/new?template=idea.yml&title=%5BIdea%5D+")
                }
                ContactOptionRow(
                    icon = Icons.Rounded.Star,
                    iconColor = starColor,
                    title = "Star Tidy on GitHub",
                    subtitle = "Help others discover Tidy"
                ) {
                    onDismiss()
                    openUrl("https://github.com/lokiT26/Tidy")
                }
                ContactOptionRow(
                    icon = Icons.Rounded.NewReleases,
                    iconColor = releaseColor,
                    title = "Releases & updates",
                    subtitle = "Current: v$pkgVersion · Check latest APK"
                ) {
                    onDismiss()
                    openUrl("https://github.com/lokiT26/Tidy/releases/latest")
                }
                ContactOptionRow(
                    icon = Icons.Rounded.Share,
                    iconColor = shareColor,
                    title = "Share Tidy",
                    subtitle = "Send download link to a friend"
                ) {
                    onDismiss()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out Tidy — a fast, minimal, 100% offline To-Do, Notes & Widgets app for Android: https://github.com/lokiT26/Tidy"
                        )
                    }
                    runCatching { ctx.startActivity(Intent.createChooser(shareIntent, "Share Tidy")) }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Note: A GitHub account is required to submit issues, ideas, or star the repo (downloading updates from Releases does not require an account).",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ContactOptionRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = tidy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(t.cardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = if (t.dark) 0.18f else 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = t.text)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = t.textMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = t.textFaint, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPlanImportSheet(
    vm: AppViewModel,
    s: AppSettings,
    categories: List<Category>,
    initialDraft: AiPlanDraft? = null,
    onDismiss: () -> Unit,
) {
    val t = tidy
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewDraft by remember(initialDraft) { mutableStateOf(initialDraft) }
    val lastUndo = remember(s.lastAiImportBatch) { AiUndoSummary.fromJson(s.lastAiImportBatch) }

    val aiFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val parsed = runCatching {
                    withContext(Dispatchers.IO) {
                        val raw = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                        Repository.parseAiPlan(raw)
                    }
                }.getOrNull()
                if (parsed != null) {
                    previewDraft = parsed
                } else {
                    Toast.makeText(ctx, "Couldn't find a valid Tidy AI plan in that file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = t.card,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(40.dp, 5.dp).clip(CircleShape).background(t.divider)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = t.accentStrong, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import AI plan", style = MaterialTheme.typography.titleLarge, color = t.text)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Plan your schedule or notes with any AI, then bring them into Tidy in one tap — 100% offline.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted
            )
            Spacer(Modifier.height(16.dp))

            val draft = previewDraft
            if (draft != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(t.accentSoft)
                        .padding(14.dp)
                ) {
                    val parts = buildList {
                        if (draft.tasks.isNotEmpty()) add("${draft.tasks.size} task" + if (draft.tasks.size == 1) "" else "s")
                        if (draft.notes.isNotEmpty()) add("${draft.notes.size} note" + if (draft.notes.size == 1) "" else "s")
                    }
                    Text(
                        "Ready to add ${parts.joinToString(" & ")}",
                        style = MaterialTheme.typography.titleSmall,
                        color = t.accentStrong
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (draft.tasks.isNotEmpty()) {
                    Text("TASKS (${draft.tasks.size})", style = MaterialTheme.typography.labelSmall, color = t.textMuted)
                    Spacer(Modifier.height(6.dp))
                    draft.tasks.take(12).forEach { td ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(t.cardAlt)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(td.title, style = MaterialTheme.typography.bodyMedium, color = t.text)
                            val meta = buildList {
                                td.dueDate?.let { add(Dates.label(it)) }
                                td.dueTime?.let { add(Dates.time(it)) }
                                td.reminderOffset?.let { add("🔔 ${Dates.reminderLabel(it)}") }
                                td.listName?.let { add(it) }
                                if (td.subtasks.isNotEmpty()) add("+${td.subtasks.size} steps")
                            }
                            if (meta.isNotEmpty()) {
                                Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = t.textMuted)
                            }
                        }
                    }
                    if (draft.tasks.size > 12) {
                        Text("+${draft.tasks.size - 12} more tasks…", style = MaterialTheme.typography.labelSmall, color = t.textFaint, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (draft.notes.isNotEmpty()) {
                    Text("NOTES (${draft.notes.size})", style = MaterialTheme.typography.labelSmall, color = t.textMuted)
                    Spacer(Modifier.height(6.dp))
                    draft.notes.take(8).forEach { nd ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(t.cardAlt)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(
                                nd.title.ifBlank { nd.content.lines().firstOrNull()?.removePrefix("[ ] ") ?: "Note" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.text
                            )
                            Text(
                                if (nd.isChecklist) "Checklist note" else "Text note",
                                style = MaterialTheme.typography.labelSmall,
                                color = t.textMuted
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { if (initialDraft != null) onDismiss() else previewDraft = null },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(50)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            scope.launch {
                                val summary = withContext(Dispatchers.IO) { vm.repo.importAiPlan(draft) }
                                Toast.makeText(ctx, "Added ${summary.label()} ✓", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1.6f).height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = t.accentStrong)
                    ) { Text("Add to Tidy", color = Color.White) }
                }
            } else {
                // Step 1: Copy prompt
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.cardAlt)
                        .padding(16.dp)
                ) {
                    Text("1. Copy prompt for your AI", style = MaterialTheme.typography.titleSmall, color = t.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Includes today's date, your existing lists, and Tidy's task & note format.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val prompt = Repository.buildAiPrompt(categories)
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Tidy AI Prompt", prompt))
                            Toast.makeText(ctx, "AI prompt copied — paste it into any AI", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = t.accentStrong)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy AI prompt", color = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Step 2: Import from clipboard or file
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(t.cardAlt)
                        .padding(16.dp)
                ) {
                    Text("2. Import AI reply", style = MaterialTheme.typography.titleSmall, color = t.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Copy the AI's JSON response (or share it to Tidy), then paste or pick a file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                                val parsed = Repository.parseAiPlan(text)
                                if (parsed != null) {
                                    previewDraft = parsed
                                } else {
                                    Toast.makeText(ctx, "No Tidy AI plan found in clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1.3f).height(46.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = t.accentStrong)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste plan", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = { aiFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("File")
                        }
                    }
                }

                if (lastUndo != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Palette.danger.copy(alpha = 0.10f))
                            .border(1.dp, Palette.danger.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                            .clickable {
                                scope.launch {
                                    val undone = withContext(Dispatchers.IO) { vm.repo.undoLastAiImport() }
                                    if (undone != null) {
                                        Toast.makeText(ctx, "Undid last import (${undone.label()})", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = Palette.danger, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Undo last AI import", style = MaterialTheme.typography.titleSmall, color = Palette.danger)
                            Text("Removes ${lastUndo.label()} from the last batch", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                        }
                    }
                }
            }
        }
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
