package com.lokesh.tidy

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lokesh.tidy.data.Category
import com.lokesh.tidy.data.Note
import com.lokesh.tidy.data.TaskWithSubtasks
import com.lokesh.tidy.reminder.Reminders
import com.lokesh.tidy.ui.AppViewModel
import com.lokesh.tidy.ui.components.AmbientBackground
import com.lokesh.tidy.ui.components.glass
import com.lokesh.tidy.ui.screens.*
import com.lokesh.tidy.ui.theme.TidyTheme
import com.lokesh.tidy.ui.theme.tidy
import com.lokesh.tidy.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Reminders.ensureChannel(this)
        Reminders.scheduleMidnight(this)
        lifecycleScope.launch(Dispatchers.IO) { WidgetUpdater.updateAll(this@MainActivity) }
        pendingIntent.value = intent
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            TidyTheme(s) {
                val t = tidy
                LaunchedEffect(t.dark) {
                    enableEdgeToEdge(
                        statusBarStyle = if (t.dark) SystemBarStyle.dark(Color.Transparent.toArgb()) else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
                        navigationBarStyle = if (t.dark) SystemBarStyle.dark(Color.Transparent.toArgb()) else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
                    )
                }
                TidyApp(vm, s, pendingIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }

    companion object {
        const val EXTRA_ACTION = "tidy_action"
        const val ACTION_NEW_TASK = "new_task"
        const val ACTION_NEW_NOTE = "new_note"
        const val ACTION_OPEN_TASK = "open_task"
        const val ACTION_OPEN_NOTE = "open_note"
        const val ACTION_CALENDAR = "calendar"
        const val EXTRA_ID = "id"

        fun parseAction(intent: Intent?): Pair<String?, Long> {
            if (intent == null) return null to -1L
            val extraAction = intent.getStringExtra(EXTRA_ACTION)
            val extraId = intent.getLongExtra(EXTRA_ID, -1L)
            if (extraAction != null) return extraAction to extraId
            val uri = intent.data
            if (uri != null && uri.scheme == "tidy" && uri.host == "widget") {
                val segs = uri.pathSegments
                val act = segs.getOrNull(0)
                val id = segs.getOrNull(1)?.toLongOrNull() ?: -1L
                return act to id
            }
            return null to -1L
        }
    }
}

private enum class Overlay { None, Search, Settings, Note }

@Composable
fun TidyApp(vm: AppViewModel, s: com.lokesh.tidy.data.AppSettings, pendingIntent: MutableState<Intent?>) {
    val t = tidy
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var snackbarJob by remember { mutableStateOf<Job?>(null) }

    val initialAction = remember { MainActivity.parseAction(pendingIntent.value).first }
    var tab by rememberSaveable {
        mutableIntStateOf(
            when (initialAction) {
                MainActivity.ACTION_CALENDAR -> 1
                MainActivity.ACTION_NEW_NOTE, MainActivity.ACTION_OPEN_NOTE -> 2
                else -> 0
            }
        )
    }
    var overlay by remember {
        mutableStateOf(
            if (initialAction == MainActivity.ACTION_NEW_NOTE) Overlay.Note else Overlay.None
        )
    }
    var editingNote by remember {
        mutableStateOf<Note?>(
            if (initialAction == MainActivity.ACTION_NEW_NOTE) Note() else null
        )
    }
    var editingTask by remember { mutableStateOf<TaskWithSubtasks?>(null) }
    var newTask by remember { mutableStateOf(initialAction == MainActivity.ACTION_NEW_TASK) }
    var awaitingDeepLinkNote by remember { mutableStateOf(initialAction == MainActivity.ACTION_OPEN_NOTE) }
    var calendarDay by remember { mutableStateOf(LocalDate.now()) }
    var calendarResetTrigger by remember { mutableIntStateOf(0) }
    var selectedCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }

    // Synchronously switch tab on new intent before composition so Today screen never flashes
    val curIntent = pendingIntent.value
    val (curAction, curTargetId) = remember(curIntent) { MainActivity.parseAction(curIntent) }
    if (curAction != null) {
        when (curAction) {
            MainActivity.ACTION_CALENDAR -> {
                if (tab != 1) tab = 1
                if (overlay != Overlay.None) overlay = Overlay.None
            }
            MainActivity.ACTION_NEW_NOTE -> {
                if (tab != 2) tab = 2
                if (editingNote == null || editingNote?.id != 0L) editingNote = Note()
                if (overlay != Overlay.Note) overlay = Overlay.Note
            }
            MainActivity.ACTION_OPEN_NOTE -> {
                if (tab != 2) tab = 2
                val found = notes.find { it.id == curTargetId }
                if (found != null) {
                    editingNote = found
                    overlay = Overlay.Note
                    awaitingDeepLinkNote = false
                } else {
                    awaitingDeepLinkNote = true
                }
            }
            MainActivity.ACTION_NEW_TASK -> {
                if (overlay != Overlay.None) overlay = Overlay.None
                if (!newTask) newTask = true
            }
        }
    }

    // Consume pendingIntent and perform direct DB lookup for deep-linked task/note if not yet in Flow
    LaunchedEffect(curIntent) {
        val i = curIntent ?: return@LaunchedEffect
        val (act, id) = MainActivity.parseAction(i)
        when (act) {
            MainActivity.ACTION_NEW_TASK -> {
                overlay = Overlay.None
                newTask = true
                pendingIntent.value = null
            }
            MainActivity.ACTION_NEW_NOTE -> {
                tab = 2
                editingNote = Note()
                overlay = Overlay.Note
                pendingIntent.value = null
            }
            MainActivity.ACTION_CALENDAR -> {
                tab = 1
                calendarDay = LocalDate.now()
                overlay = Overlay.None
                pendingIntent.value = null
            }
            MainActivity.ACTION_OPEN_TASK -> {
                overlay = Overlay.None
                val task = tasks.find { it.task.id == id } ?: withContext(Dispatchers.IO) { vm.repo.dao.task(id) }
                if (task != null) editingTask = task
                pendingIntent.value = null
            }
            MainActivity.ACTION_OPEN_NOTE -> {
                tab = 2
                val note = notes.find { it.id == id } ?: withContext(Dispatchers.IO) { vm.repo.dao.note(id) }
                if (note != null) {
                    editingNote = note
                    overlay = Overlay.Note
                }
                awaitingDeepLinkNote = false
                pendingIntent.value = null
            }
            else -> {
                awaitingDeepLinkNote = false
                pendingIntent.value = null
            }
        }
    }

    fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        snackbarJob?.cancel()
        snackbar.currentSnackbarData?.dismiss()
        snackbarJob = scope.launch {
            val result = withTimeoutOrNull(8000L) {
                snackbar.showSnackbar(message, "Undo", duration = SnackbarDuration.Indefinite)
            }
            if (result == null) {
                snackbar.currentSnackbarData?.dismiss()
            } else if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            }
        }
    }

    fun toggle(task: com.lokesh.tidy.data.Task) {
        if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.setDone(task.id, !task.completed)
    }
    fun deleteWithUndo(item: TaskWithSubtasks) {
        vm.deleteTask(item.task)
        showUndoSnackbar("Task deleted") { vm.restoreTask(item) }
    }

    val todayNavIcon = remember(LocalTime.now().hour) {
        when (LocalTime.now().hour) {
            in 5..11 -> Icons.Rounded.WbTwilight
            in 12..16 -> Icons.Rounded.WbSunny
            in 17..20 -> Icons.Rounded.NightsStay
            else -> Icons.Rounded.Bedtime
        }
    }

    AmbientBackground {
        if (!awaitingDeepLinkNote) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.98f, animationSpec = tween(180))) togetherWith fadeOut(tween(100)) },
                label = "tabs"
            ) { current ->
                when (current) {
                    0 -> TodayScreen(
                        name = s.name, tasks = tasks, categories = categories, confettiEnabled = s.confetti,
                        onToggle = ::toggle, onOpen = { editingTask = it }, onDelete = ::deleteWithUndo, onSubtask = { vm.toggleSubtask(it) },
                        onReorder = { vm.reorder(it) }, onSearch = { overlay = Overlay.Search }, onSettings = { overlay = Overlay.Settings },
                        selectedCategory = selectedCategory, onSelectCategory = { selectedCategory = it },
                        onAddCategory = { editCategory = null; showCategoryDialog = true },
                    )
                    1 -> CalendarScreen(
                        tasks = tasks, categories = categories, selected = calendarDay,
                        resetToTodayTrigger = calendarResetTrigger,
                        onSelect = { calendarDay = it },
                        onToggle = ::toggle, onOpen = { editingTask = it }, onDelete = ::deleteWithUndo,
                        onSubtask = { vm.toggleSubtask(it) }, onReorder = { vm.reorder(it) }
                    )
                    2 -> NotesScreen(
                        notes = notes,
                        onOpen = { editingNote = it; overlay = Overlay.Note },
                        onTogglePin = {
                            if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.saveNote(it.copy(pinned = !it.pinned))
                        },
                        onReorder = { vm.reorderNotes(it) }
                    )
                    else -> StatsScreen(tasks, categories)
                }
            }
        }

        // Floating glass navigation
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).height(66.dp).glass(RoundedCornerShape(33.dp), t.glass, t.glassBorder, t.dark).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavItem(todayNavIcon, "Today", tab == 0) { tab = 0 }
                NavItem(Icons.Rounded.CalendarMonth, "Calendar", tab == 1) {
                    if (tab == 1) {
                        calendarDay = LocalDate.now()
                        calendarResetTrigger++
                    } else {
                        tab = 1
                    }
                }
                NavItem(Icons.AutoMirrored.Rounded.StickyNote2, "Notes", tab == 2) { tab = 2 }
                NavItem(Icons.Rounded.Insights, "Stats", tab == 3) { tab = 3 }
            }
            Spacer(Modifier.width(12.dp))
            val fabRot by animateFloatAsState(if (tab == 2) 90f else 0f, spring(dampingRatio = 0.5f), label = "fab")
            Box(
                Modifier.size(66.dp)
                    .shadow(20.dp, CircleShape, ambientColor = t.accentStrong, spotColor = t.accentStrong)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(t.accentStrong, t.accent.partner)))
                    .clickable {
                        if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (tab == 2) { editingNote = Note(); overlay = Overlay.Note } else newTask = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (tab == 2) Icons.Rounded.EditNote else Icons.Rounded.Add, "Add", tint = Color.White, modifier = Modifier.size(30.dp).graphicsLayer { rotationZ = fabRot - if (tab == 2) 90f else 0f })
            }
        }

        // 8-second Undo snackbar, swipeable either left or right to dismiss immediately
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 16.dp, end = 16.dp)) { data ->
            val offsetX = remember(data) { Animatable(0f) }
            val drag = rememberDraggableState { delta ->
                scope.launch { offsetX.snapTo(offsetX.value + delta) }
            }
            Box(
                Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .graphicsLayer { alpha = (1f - abs(offsetX.value) / 420f).coerceIn(0f, 1f) }
                    .draggable(
                        state = drag,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity ->
                            if (abs(offsetX.value) > 110f || abs(velocity) > 900f) {
                                data.dismiss()
                            } else {
                                offsetX.animateTo(0f, spring(stiffness = 500f))
                            }
                        }
                    )
            ) {
                Snackbar(data, shape = RoundedCornerShape(18.dp), containerColor = if (t.dark) Color(0xFF2E2E36) else Color(0xFF1E1E24), contentColor = Color.White, actionColor = t.accent.pastel)
            }
        }

        // Overlays
        AnimatedVisibility(
            overlay != Overlay.None,
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it / 6 } + fadeIn(),
            exit = slideOutVertically(tween(180)) { it / 6 } + fadeOut(tween(180)),
        ) {
            val current = remember { mutableStateOf(overlay) }.also { if (overlay != Overlay.None) it.value = overlay }.value
            AmbientBackground {
                when (current) {
                    Overlay.Search -> SearchScreen(
                        tasks = tasks, notes = notes, categories = categories,
                        onToggle = ::toggle, onOpenTask = { editingTask = it }, onDeleteTask = ::deleteWithUndo,
                        onOpenNote = { editingNote = it; overlay = Overlay.Note }, onSubtask = { vm.toggleSubtask(it) }
                    ) { overlay = Overlay.None }
                    Overlay.Settings -> SettingsScreen(vm, s, categories, { editCategory = it; showCategoryDialog = true }) { overlay = Overlay.None }
                    Overlay.Note -> editingNote?.let { n ->
                        key(n.id, n.createdAt) {
                            NoteEditor(n, onSave = { vm.saveNote(it) }, onDelete = { del ->
                                vm.deleteNote(del)
                                showUndoSnackbar("Note deleted") { vm.restoreNote(del) }
                            }, onClose = { overlay = Overlay.None; editingNote = null })
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    if (newTask || editingTask != null) {
        TaskEditorSheet(
            initial = editingTask,
            categories = categories,
            defaultDate = if (tab == 1) calendarDay.toEpochDay() else LocalDate.now().toEpochDay(),
            defaultCategory = selectedCategory ?: s.lastCategoryId,
            defaultPriority = s.lastPriority,
            onSave = { task, subs -> vm.saveTask(task, subs); newTask = false; editingTask = null },
            onDelete = { _ -> editingTask?.let { deleteWithUndo(it) }; editingTask = null; newTask = false },
            onDismiss = { newTask = false; editingTask = null },
        )
    }
    if (showCategoryDialog) {
        CategoryDialog(editCategory, onSave = { vm.saveCategory(it); showCategoryDialog = false },
            onDelete = { vm.deleteCategory(it); if (selectedCategory == it.id) selectedCategory = null; showCategoryDialog = false },
            onDismiss = { showCategoryDialog = false })
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val t = tidy
    val bg by animateColorAsState(if (selected) t.accentStrong.copy(alpha = if (t.dark) 0.22f else 0.14f) else Color.Transparent, label = "nb")
    val tint by animateColorAsState(if (selected) t.accentStrong else t.textMuted, label = "nt")
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 11.dp)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(23.dp))
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
        }
    }
}
