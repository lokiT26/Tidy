package com.lokesh.tidy.data

import android.content.Context
import androidx.room.Room
import com.lokesh.tidy.reminder.Reminders
import com.lokesh.tidy.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class Repository private constructor(private val context: Context) {
    val db: TidyDatabase = Room.databaseBuilder(context, TidyDatabase::class.java, "tidy.db").build()
    val dao = db.dao()
    val settings = SettingsStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tasks = dao.observeTasks()
    val categories = dao.observeCategories()
    val notes = dao.observeNotes()

    private fun changed() {
        scope.launch { WidgetUpdater.updateAll(context) }
    }

    // ---------- Tasks ----------
    suspend fun saveTask(task: Task, subtasks: List<Subtask>): Long {
        val id = if (task.id == 0L) {
            dao.insertTask(task.copy(sortOrder = dao.minSort() - 1))
        } else {
            dao.updateTask(task); task.id
        }
        dao.deleteSubtasksOf(id)
        dao.insertSubtasks(subtasks.mapIndexed { i, s -> s.copy(id = 0, taskId = id, sortOrder = i) })
        dao.task(id)?.task?.let { Reminders.schedule(context, it) }
        changed()
        return id
    }

    suspend fun setCompleted(taskId: Long, done: Boolean) {
        val tws = dao.task(taskId) ?: return
        val t = tws.task
        if (t.completed == done) return
        dao.updateTask(t.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null))
        if (done) {
            Reminders.cancel(context, t.id)
            if (t.repeat != "NONE" && t.dueDate != null) {
                val next = nextOccurrence(LocalDate.ofEpochDay(t.dueDate), t.repeat)
                val newTask = t.copy(id = 0, completed = false, completedAt = null, dueDate = next.toEpochDay(), createdAt = System.currentTimeMillis())
                val newId = dao.insertTask(newTask)
                dao.insertSubtasks(tws.subtasks.mapIndexed { i, s -> s.copy(id = 0, taskId = newId, done = false, sortOrder = i) })
                Reminders.schedule(context, newTask.copy(id = newId))
            }
        } else {
            Reminders.schedule(context, t.copy(completed = false))
        }
        changed()
    }

    suspend fun toggleSubtask(s: Subtask) {
        dao.updateSubtask(s.copy(done = !s.done)); changed()
    }

    suspend fun deleteTask(t: Task) {
        Reminders.cancel(context, t.id); dao.deleteTask(t); changed()
    }

    suspend fun restoreTask(tws: TaskWithSubtasks) {
        val id = dao.insertTask(tws.task)
        dao.insertSubtasks(tws.subtasks.map { it.copy(id = 0, taskId = id) })
        Reminders.schedule(context, tws.task.copy(id = id)); changed()
    }

    suspend fun reorder(ordered: List<Task>) {
        dao.updateTasks(ordered.mapIndexed { i, t -> t.copy(sortOrder = i) }); changed()
    }

    suspend fun snooze(taskId: Long, minutes: Int) {
        val t = dao.task(taskId)?.task ?: return
        Reminders.scheduleAt(context, t, System.currentTimeMillis() + minutes * 60_000L)
    }

    // ---------- Categories ----------
    suspend fun saveCategory(c: Category) { if (c.id == 0L) dao.insertCategory(c) else dao.updateCategory(c); changed() }
    suspend fun deleteCategory(c: Category) { dao.clearCategory(c.id); dao.deleteCategory(c); changed() }

    // ---------- Notes ----------
    suspend fun saveNote(n: Note): Long {
        val id = if (n.id == 0L) dao.insertNote(n) else { dao.updateNote(n); n.id }
        changed(); return id
    }
    suspend fun deleteNote(n: Note) { dao.deleteNote(n); changed() }

    // ---------- Backup ----------
    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("categories", JSONArray().apply {
            dao.allCategories().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.colorIndex).put("emoji", it.emoji).put("sort", it.sortOrder)) }
        })
        root.put("tasks", JSONArray().apply {
            dao.allTasks().forEach { tw ->
                val t = tw.task
                put(JSONObject().put("id", t.id).put("title", t.title).put("notes", t.notes)
                    .put("dueDate", t.dueDate ?: JSONObject.NULL).put("dueTime", t.dueTime ?: JSONObject.NULL)
                    .put("reminder", t.reminderOffset ?: JSONObject.NULL).put("priority", t.priority)
                    .put("categoryId", t.categoryId ?: JSONObject.NULL).put("completed", t.completed)
                    .put("completedAt", t.completedAt ?: JSONObject.NULL).put("createdAt", t.createdAt)
                    .put("sort", t.sortOrder).put("repeat", t.repeat)
                    .put("subtasks", JSONArray().apply { tw.subtasks.forEach { put(JSONObject().put("title", it.title).put("done", it.done)) } }))
            }
        })
        root.put("notes", JSONArray().apply {
            dao.allNotes().forEach { put(JSONObject().put("title", it.title).put("content", it.content).put("color", it.colorIndex).put("pinned", it.pinned).put("checklist", it.isChecklist).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)) }
        })
        return root.toString(2)
    }

    suspend fun importJson(json: String) {
        val root = JSONObject(json)
        Reminders.cancelAll(context, dao.allTasks().map { it.task.id })
        dao.clearTasks(); dao.clearNotes(); dao.clearCategories()
        val cats = root.optJSONArray("categories") ?: JSONArray()
        dao.insertCategoriesRaw((0 until cats.length()).map { i ->
            val o = cats.getJSONObject(i)
            Category(o.getLong("id"), o.getString("name"), o.optInt("color"), o.optString("emoji"), o.optInt("sort"))
        })
        val tasks = root.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until tasks.length()) {
            val o = tasks.getJSONObject(i)
            fun lOrNull(k: String) = if (o.isNull(k)) null else o.getLong(k)
            fun iOrNull(k: String) = if (o.isNull(k)) null else o.getInt(k)
            val t = Task(
                title = o.getString("title"), notes = o.optString("notes"), dueDate = lOrNull("dueDate"), dueTime = iOrNull("dueTime"),
                reminderOffset = iOrNull("reminder"), priority = o.optInt("priority"), categoryId = lOrNull("categoryId"),
                completed = o.optBoolean("completed"), completedAt = lOrNull("completedAt"), createdAt = o.optLong("createdAt"),
                sortOrder = o.optInt("sort"), repeat = o.optString("repeat", "NONE"),
            )
            val id = dao.insertTask(t)
            val subs = o.optJSONArray("subtasks") ?: JSONArray()
            dao.insertSubtasks((0 until subs.length()).map { j -> val s = subs.getJSONObject(j); Subtask(taskId = id, title = s.getString("title"), done = s.optBoolean("done"), sortOrder = j) })
            Reminders.schedule(context, t.copy(id = id))
        }
        val notes = root.optJSONArray("notes") ?: JSONArray()
        dao.insertNotesRaw((0 until notes.length()).map { i ->
            val o = notes.getJSONObject(i)
            Note(title = o.optString("title"), content = o.optString("content"), colorIndex = o.optInt("color"), pinned = o.optBoolean("pinned"), isChecklist = o.optBoolean("checklist"), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"))
        })
        changed()
    }

    suspend fun seedIfEmpty() {
        if (settings.seeded()) return
        if (dao.allCategories().isEmpty()) {
            dao.insertCategoriesRaw(listOf(
                Category(name = "Personal", colorIndex = 0, emoji = "🌿", sortOrder = 0),
                Category(name = "Work", colorIndex = 3, emoji = "💼", sortOrder = 1),
                Category(name = "Health", colorIndex = 2, emoji = "💪", sortOrder = 2),
            ))
            val today = LocalDate.now().toEpochDay()
            val cats = dao.allCategories()
            dao.insertTask(Task(title = "Welcome to Tidy ✨", notes = "Swipe right to complete, left to delete. Long-press to reorder.", dueDate = today, priority = 2, categoryId = cats[0].id))
            val id = dao.insertTask(Task(title = "Try adding a widget to home screen", dueDate = today, categoryId = cats[0].id, priority = 1))
            dao.insertSubtasks(listOf(Subtask(taskId = id, title = "Long-press home screen", sortOrder = 0), Subtask(taskId = id, title = "Widgets → Tidy", sortOrder = 1)))
            dao.insertNote(Note(title = "Ideas 💡", content = "Notes live here — pick a colour, pin the important ones.", colorIndex = 5, pinned = true))
        }
        settings.setSeeded()
        changed()
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }

        fun nextOccurrence(from: LocalDate, repeat: String): LocalDate {
            val today = LocalDate.now()
            var d = from
            do {
                d = when (repeat) {
                    "DAILY" -> d.plusDays(1)
                    "WEEKDAYS" -> { var n = d.plusDays(1); while (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) n = n.plusDays(1); n }
                    "WEEKLY" -> d.plusWeeks(1)
                    "MONTHLY" -> d.plusMonths(1)
                    "YEARLY" -> d.plusYears(1)
                    else -> d.plusDays(1)
                }
            } while (d.isBefore(today))
            return d
        }

        fun streaks(tasks: List<Task>): Pair<Int, Int> {
            val zone = ZoneId.systemDefault()
            val days = tasks.mapNotNull { it.completedAt }.map { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }.toSortedSet()
            if (days.isEmpty()) return 0 to 0
            var best = 1; var run = 1; var prev: Long? = null
            for (d in days) { if (prev != null) { run = if (d == prev + 1) run + 1 else 1; best = maxOf(best, run) }; prev = d }
            val today = LocalDate.now().toEpochDay()
            var cur = 0
            var d = if (days.contains(today)) today else today - 1
            while (days.contains(d)) { cur++; d-- }
            return cur to best
        }
    }
}
