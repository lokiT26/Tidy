package com.lokesh.tidy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lokesh.tidy.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository.get(app)

    val settings: StateFlow<AppSettings?> = repo.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val tasks: StateFlow<List<TaskWithSubtasks>> = repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<Note>> = repo.notes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { repo.seedIfEmpty() } }

    fun saveTask(t: Task, subs: List<Subtask>) = viewModelScope.launch { repo.saveTask(t, subs) }
    fun setDone(id: Long, done: Boolean) = viewModelScope.launch { repo.setCompleted(id, done) }
    fun toggleSubtask(s: Subtask) = viewModelScope.launch { repo.toggleSubtask(s) }
    fun deleteTask(t: Task) = viewModelScope.launch { repo.deleteTask(t) }
    fun restoreTask(t: TaskWithSubtasks) = viewModelScope.launch { repo.restoreTask(t) }
    fun reorder(list: List<Task>) = viewModelScope.launch { repo.reorder(list) }
    fun saveCategory(c: Category) = viewModelScope.launch { repo.saveCategory(c) }
    fun deleteCategory(c: Category) = viewModelScope.launch { repo.deleteCategory(c) }
    fun reorderCategories(list: List<Category>) = viewModelScope.launch { repo.reorderCategories(list) }
    fun saveNote(n: Note, onId: (Long) -> Unit = {}) = viewModelScope.launch { onId(repo.saveNote(n)) }
    fun deleteNote(n: Note) = viewModelScope.launch { repo.deleteNote(n) }
    fun restoreNote(n: Note) = viewModelScope.launch { repo.saveNote(n.copy(id = 0)) }
    fun reorderNotes(list: List<Note>) = viewModelScope.launch { repo.reorderNotes(list) }

    fun setName(v: String) = viewModelScope.launch { repo.settings.setName(v) }
    fun setTheme(v: Int) = viewModelScope.launch { repo.settings.setTheme(v) }
    fun setAmoled(v: Boolean) = viewModelScope.launch { repo.settings.setAmoled(v) }
    fun setAccent(v: Int) = viewModelScope.launch { repo.settings.setAccent(v) }
    fun setConfetti(v: Boolean) = viewModelScope.launch { repo.settings.setConfetti(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { repo.settings.setHaptics(v) }
    fun setNoteSizeMode(v: Int) = viewModelScope.launch { repo.settings.setNoteSizeMode(v) }
    fun setSeparatePinnedNoteSize(v: Boolean) = viewModelScope.launch { repo.settings.setSeparatePinnedNoteSize(v) }
    fun setPinnedNoteSizeMode(v: Int) = viewModelScope.launch { repo.settings.setPinnedNoteSizeMode(v) }
    fun setNotesSortOrder(v: Int) = viewModelScope.launch { repo.settings.setNotesSortOrder(v) }
}

object Dates {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val longFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    fun today() = LocalDate.now().toEpochDay()

    fun label(epochDay: Long): String {
        val t = today()
        return when (epochDay) {
            t -> "Today"; t + 1 -> "Tomorrow"; t - 1 -> "Yesterday"
            else -> LocalDate.ofEpochDay(epochDay).format(dayFmt)
        }
    }
    fun long(d: LocalDate): String = d.format(longFmt)
    fun short(d: LocalDate): String = d.format(dayFmt)

    fun time(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }

    fun repeatLabel(r: String) = when (r) {
        "DAILY" -> "Daily"; "WEEKDAYS" -> "Weekdays"; "WEEKLY" -> "Weekly"; "MONTHLY" -> "Monthly"; "YEARLY" -> "Yearly"; else -> "Never"
    }
    fun reminderLabel(o: Int?) = when {
        o == null -> "No reminder"
        o == 0 -> "At time"
        o == 1440 -> "1 day before"
        o > 1440 && o % 1440 == 0 -> "${o / 1440} days before"
        o == 60 -> "1 hour before"
        o > 60 && o % 60 == 0 -> "${o / 60} hr before"
        o < 60 -> "$o min before"
        else -> {
            val d = o / 1440
            val h = (o % 1440) / 60
            val m = o % 60
            buildList {
                if (d > 0) add("${d}d")
                if (h > 0) add("${h}h")
                if (m > 0) add("${m}m")
            }.joinToString(" ") + " before"
        }
    }

    fun reminderPreview(dueDate: Long?, dueTime: Int?, offset: Int?): String? {
        if (dueDate == null || offset == null) return null
        val baseMinutes = (dueTime ?: (9 * 60)).toLong() - offset.toLong()
        val ldt = LocalDate.ofEpochDay(dueDate).atStartOfDay().plusMinutes(baseMinutes)
        val dayStr = label(ldt.toLocalDate().toEpochDay())
        val timeStr = time(ldt.hour * 60 + ldt.minute)
        return "$dayStr at $timeStr"
    }
}
