package com.lokesh.tidy.data

import android.content.Context
import androidx.room.Room
import com.lokesh.tidy.reminder.Reminders
import com.lokesh.tidy.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

data class AiTaskDraft(
    val title: String,
    val notes: String = "",
    val dueDate: Long? = null,
    val dueTime: Int? = null,
    val reminderOffset: Int? = null,
    val priority: Int = 0,
    val listName: String? = null,
    val listEmoji: String = "",
    val repeat: String = "NONE",
    val subtasks: List<String> = emptyList(),
)

data class AiNoteDraft(
    val title: String = "",
    val content: String = "",
    val colorIndex: Int = 0,
    val pinned: Boolean = false,
    val isChecklist: Boolean = false,
    val hidden: Boolean = false,
)

data class AiPlanDraft(
    val tasks: List<AiTaskDraft>,
    val notes: List<AiNoteDraft>,
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && notes.isEmpty()
}

data class AiUndoSummary(
    val taskIds: List<Long>,
    val noteIds: List<Long>,
    val categoryIds: List<Long>,
) {
    val isEmpty: Boolean get() = taskIds.isEmpty() && noteIds.isEmpty() && categoryIds.isEmpty()

    fun label(): String {
        val parts = buildList {
            if (taskIds.isNotEmpty()) add("${taskIds.size} task" + if (taskIds.size == 1) "" else "s")
            if (noteIds.isNotEmpty()) add("${noteIds.size} note" + if (noteIds.size == 1) "" else "s")
        }
        return parts.joinToString(" & ")
    }

    fun toJson(): String =
        "${taskIds.joinToString(",")}|${noteIds.joinToString(",")}|${categoryIds.joinToString(",")}"

    companion object {
        fun fromJson(raw: String): AiUndoSummary? = runCatching {
            if (raw.isBlank()) return null
            val parts = raw.split("|")
            if (parts.size != 3) return null
            fun parseLongs(s: String): List<Long> =
                s.split(",").mapNotNull { it.trim().toLongOrNull() }
            AiUndoSummary(
                taskIds = parseLongs(parts[0]),
                noteIds = parseLongs(parts[1]),
                categoryIds = parseLongs(parts[2]),
            ).takeIf { !it.isEmpty }
        }.getOrNull()
    }
}

class Repository private constructor(private val context: Context) {
    val db: TidyDatabase = Room.databaseBuilder(context, TidyDatabase::class.java, "tidy.db")
        .addMigrations(TidyDatabase.MIGRATION_1_2, TidyDatabase.MIGRATION_2_3)
        .build()
    val dao = db.dao()
    val settings = SettingsStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var widgetSyncJob: Job? = null

    val tasks = dao.observeTasks()
    val categories = dao.observeCategories()
    val notes = dao.observeNotes()

    private fun changed() {
        widgetSyncJob?.cancel()
        widgetSyncJob = scope.launch {
            delay(150)
            WidgetUpdater.updateAll(context)
        }
    }

    // ---------- Tasks ----------
    suspend fun saveTask(task: Task, subtasks: List<Subtask>): Long {
        settings.setLastEditorDefaults(task.priority, task.categoryId)
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
        if (done) {
            Reminders.cancel(context, t.id)
            var spawnedId: Long? = t.spawnedTaskId
            if (t.repeat != "NONE" && t.dueDate != null) {
                val next = nextOccurrence(LocalDate.ofEpochDay(t.dueDate), t.repeat)
                val nextEpoch = next.toEpochDay()
                val existingById = spawnedId?.let { dao.task(it)?.task }
                val existingByMatch = dao.findRecurringInstance(t.title, t.repeat, nextEpoch)
                val existing = existingById ?: existingByMatch
                if (existing != null) {
                    spawnedId = existing.id
                } else {
                    val newTask = t.copy(
                        id = 0,
                        completed = false,
                        completedAt = null,
                        dueDate = nextEpoch,
                        createdAt = System.currentTimeMillis(),
                        spawnedTaskId = null,
                    )
                    val newId = dao.insertTask(newTask)
                    dao.insertSubtasks(tws.subtasks.mapIndexed { i, s -> s.copy(id = 0, taskId = newId, done = false, sortOrder = i) })
                    Reminders.schedule(context, newTask.copy(id = newId))
                    spawnedId = newId
                }
            }
            dao.updateTask(t.copy(completed = true, completedAt = System.currentTimeMillis(), spawnedTaskId = spawnedId))
        } else {
            // If this recurring task had spawned a future uncompleted instance, remove that auto-spawned instance
            val spawned = t.spawnedTaskId?.let { dao.task(it)?.task }
            if (spawned != null && !spawned.completed) {
                Reminders.cancel(context, spawned.id)
                dao.deleteTask(spawned)
            }
            val updated = t.copy(completed = false, completedAt = null, spawnedTaskId = null)
            dao.updateTask(updated)
            Reminders.schedule(context, updated)
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
        val id = if (n.id == 0L) dao.insertNote(n.copy(sortOrder = dao.minNoteSort() - 1)) else { dao.updateNote(n); n.id }
        changed(); return id
    }
    suspend fun deleteNote(n: Note) { dao.deleteNote(n); changed() }
    suspend fun reorderNotes(ordered: List<Note>) {
        dao.updateNotes(ordered.mapIndexed { i, n -> n.copy(sortOrder = i) }); changed()
    }

    // ---------- Backup ----------
    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("version", 2)
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
            dao.allNotes().forEach { put(JSONObject().put("title", it.title).put("content", it.content).put("color", it.colorIndex).put("pinned", it.pinned).put("checklist", it.isChecklist).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("sort", it.sortOrder).put("hidden", it.hidden)) }
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
            Note(title = o.optString("title"), content = o.optString("content"), colorIndex = o.optInt("color"), pinned = o.optBoolean("pinned"), isChecklist = o.optBoolean("checklist"), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"), sortOrder = o.optInt("sort", i), hidden = o.optBoolean("hidden", false))
        })
        changed()
    }

    // ---------- AI Plan Import & Undo ----------
    suspend fun importAiPlan(plan: AiPlanDraft): AiUndoSummary {
        val existingCats = dao.allCategories().toMutableList()
        val createdCatIds = mutableListOf<Long>()
        val createdTaskIds = mutableListOf<Long>()
        val createdNoteIds = mutableListOf<Long>()

        var nextTaskSort = dao.minSort() - 1
        for (draft in plan.tasks) {
            val catId: Long? = if (draft.listName.isNullOrBlank() || draft.listName.equals("Inbox", ignoreCase = true)) {
                null
            } else {
                val found = existingCats.find { it.name.equals(draft.listName.trim(), ignoreCase = true) }
                if (found != null) {
                    found.id
                } else {
                    val newCat = Category(
                        name = draft.listName.trim().take(28),
                        colorIndex = existingCats.size % 7,
                        emoji = draft.listEmoji.ifBlank { "📌" },
                        sortOrder = existingCats.size
                    )
                    val newCatId = dao.insertCategory(newCat)
                    existingCats.add(newCat.copy(id = newCatId))
                    createdCatIds.add(newCatId)
                    newCatId
                }
            }
            val effectiveDueTime = if (draft.dueDate != null && draft.dueTime == null && draft.reminderOffset != null) 9 * 60 else draft.dueTime
            val task = Task(
                title = draft.title.trim().take(150),
                notes = draft.notes.trim().take(2000),
                dueDate = draft.dueDate,
                dueTime = if (draft.dueDate == null) null else effectiveDueTime,
                reminderOffset = if (draft.dueDate == null) null else draft.reminderOffset,
                priority = draft.priority.coerceIn(0, 3),
                categoryId = catId,
                repeat = if (draft.dueDate == null) "NONE" else draft.repeat,
                sortOrder = nextTaskSort--
            )
            val taskId = dao.insertTask(task)
            createdTaskIds.add(taskId)
            if (draft.subtasks.isNotEmpty()) {
                dao.insertSubtasks(
                    draft.subtasks.mapIndexedNotNull { idx, sub ->
                        sub.trim().take(120).takeIf { it.isNotEmpty() }?.let {
                            Subtask(taskId = taskId, title = it, done = false, sortOrder = idx)
                        }
                    }
                )
            }
            Reminders.schedule(context, task.copy(id = taskId))
        }

        var nextNoteSort = dao.minNoteSort() - 1
        for (nd in plan.notes) {
            val note = Note(
                title = nd.title.trim(),
                content = nd.content.trim(),
                colorIndex = nd.colorIndex.coerceIn(0, 7),
                pinned = nd.pinned,
                isChecklist = nd.isChecklist,
                hidden = nd.hidden,
                sortOrder = nextNoteSort--
            )
            val noteId = dao.insertNote(note)
            createdNoteIds.add(noteId)
        }

        val summary = AiUndoSummary(createdTaskIds, createdNoteIds, createdCatIds)
        settings.setLastAiImportBatch(summary.toJson())
        changed()
        return summary
    }

    suspend fun undoLastAiImport(): AiUndoSummary? {
        val raw = settings.current().lastAiImportBatch
        val summary = AiUndoSummary.fromJson(raw) ?: return null
        if (summary.isEmpty) {
            settings.setLastAiImportBatch("")
            return null
        }
        for (tid in summary.taskIds) {
            Reminders.cancel(context, tid)
            dao.task(tid)?.task?.let { dao.deleteTask(it) }
        }
        for (nid in summary.noteIds) {
            dao.note(nid)?.let { dao.deleteNote(it) }
        }
        val allCats = dao.allCategories()
        for (cid in summary.categoryIds) {
            allCats.find { it.id == cid }?.let {
                dao.clearCategory(it.id)
                dao.deleteCategory(it)
            }
        }
        settings.setLastAiImportBatch("")
        changed()
        return summary
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

        fun nextOccurrence(from: LocalDate, repeat: String, today: LocalDate = LocalDate.now()): LocalDate {
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

        fun streaks(tasks: List<Task>, todayDate: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
            val days = tasks.mapNotNull { it.completedAt }.map { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }.toSortedSet()
            if (days.isEmpty()) return 0 to 0
            var best = 1; var run = 1; var prev: Long? = null
            for (d in days) { if (prev != null) { run = if (d == prev + 1) run + 1 else 1; best = maxOf(best, run) }; prev = d }
            val today = todayDate.toEpochDay()
            var cur = 0
            var d = if (days.contains(today)) today else today - 1
            while (days.contains(d)) { cur++; d-- }
            return cur to best
        }

        fun buildAiPrompt(categories: List<Category>, today: LocalDate = LocalDate.now()): String {
            val listsStr = if (categories.isEmpty()) "Personal, Work, Health"
            else categories.joinToString(", ") { "${it.emoji} ${it.name}".trim() }
            return """
You are generating a structured plan for my Android app "Tidy" (To-Dos & Notes).
Today's date is $today (${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}).
My existing Tidy lists are: $listsStr (or omit "list" for Inbox, or name a new list if needed).

RULES:
1. Reply with ONLY a valid JSON object inside a ```json code block matching the schema below.
2. Only generate what I ask for (tasks only, notes only, or a combination of both when appropriate).
3. Choose the smartest structure for the specific request, context, and scenario:
   - You have full flexibility across tasks, subtasks, details ("notes"), dates, times, reminders, repeat rules, priorities, lists, text notes, and checklist notes.
   - Decide wisely how to organize the plan (when to split items into separate timed tasks vs. group steps as subtasks vs. use recurring tasks vs. attach a reference note/checklist) so the result feels practical, well-organized, and effortless to use.
4. Keep text crisp and visually clean on mobile screens & home-screen widgets:
   - Task "title": Short and scannable (2–7 words, under ~45 chars). Put extra details in "notes" or "subtasks".
   - Each subtask: Concise and clear (under 15–16 words).
   - Note "title": Short (1–5 words, under ~32 chars) so it fits cleanly on a 2-column grid card.

SCHEMA:
```json
{
  "tasks": [
    {
      "title": "Short task title (2-7 words)",
      "notes": "Optional context or details",
      "date": "YYYY-MM-DD (or null for Someday)",
      "time": "HH:mm (24h, e.g. 16:30, or null for All-day)",
      "reminderMinutesBefore": 15,
      "priority": "none | low | medium | high",
      "list": "Existing or new list name",
      "repeat": "NONE | DAILY | WEEKDAYS | WEEKLY | MONTHLY | YEARLY",
      "subtasks": ["Clear step under 15-16 words"]
    }
  ],
  "notes": [
    {
      "title": "Short note title (1-5 words)",
      "checklist": false,
      "content": "Text body for regular notes (omit if checklist is true)",
      "items": ["Checklist item 1", "Checklist item 2"],
      "color": "default | lavender | peach | mint | sky | butter | rose | sage",
      "pinned": false
    }
  ]
}
```

My request: 
""".trimIndent()
        }

        fun parseAiPlan(raw: String, today: LocalDate = LocalDate.now()): AiPlanDraft? = runCatching {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val jsonCandidate = extractJsonObjectOrArray(trimmed) ?: return null
            val root: JSONObject = if (jsonCandidate.startsWith("[")) {
                JSONObject().put("tasks", JSONArray(jsonCandidate))
            } else {
                JSONObject(jsonCandidate)
            }
            if (!root.has("tasks") && !root.has("notes")) return null

            val tasksArr = root.optJSONArray("tasks") ?: JSONArray()
            val notesArr = root.optJSONArray("notes") ?: JSONArray()

            val parsedTasks = mutableListOf<AiTaskDraft>()
            for (i in 0 until tasksArr.length()) {
                val o = tasksArr.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                if (title.isEmpty()) continue
                val notes = o.optString("notes", o.optString("description", "")).trim()
                val rawDate = if (!o.isNull("date")) o.opt("date") else if (!o.isNull("dueDate")) o.opt("dueDate") else null
                val dueDate = parseFlexibleDate(rawDate, today)
                val rawTime = if (!o.isNull("time")) o.opt("time") else if (!o.isNull("dueTime")) o.opt("dueTime") else null
                val dueTime = parseFlexibleTime(rawTime)
                val reminderOffset = when {
                    !o.isNull("reminderMinutesBefore") -> o.optInt("reminderMinutesBefore", -1).takeIf { it >= 0 }
                    !o.isNull("reminder") -> o.optInt("reminder", -1).takeIf { it >= 0 }
                    else -> null
                }
                val priority = parseFlexiblePriority(o.opt("priority"))
                val rawList = o.optString("list", o.optString("category", "")).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
                val repeatRaw = o.optString("repeat", "NONE").trim().uppercase()
                val repeat = if (repeatRaw in setOf("NONE", "DAILY", "WEEKDAYS", "WEEKLY", "MONTHLY", "YEARLY")) repeatRaw else "NONE"

                val subsArr = o.optJSONArray("subtasks") ?: JSONArray()
                val subtasks = (0 until subsArr.length()).mapNotNull { j ->
                    val el = subsArr.opt(j)
                    when (el) {
                        is JSONObject -> el.optString("title", el.optString("text", "")).trim().takeIf { it.isNotEmpty() }
                        is String -> el.trim().takeIf { it.isNotEmpty() }
                        else -> null
                    }
                }
                parsedTasks.add(
                    AiTaskDraft(
                        title = title,
                        notes = notes,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        reminderOffset = reminderOffset,
                        priority = priority,
                        listName = rawList,
                        listEmoji = o.optString("emoji", "").trim(),
                        repeat = repeat,
                        subtasks = subtasks
                    )
                )
            }

            val parsedNotes = mutableListOf<AiNoteDraft>()
            for (i in 0 until notesArr.length()) {
                val o = notesArr.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                val itemsArr = o.optJSONArray("items")
                val isChecklist = o.optBoolean("checklist", itemsArr != null && itemsArr.length() > 0)
                val content = if (isChecklist && itemsArr != null && itemsArr.length() > 0) {
                    (0 until itemsArr.length()).mapNotNull { j ->
                        when (val el = itemsArr.opt(j)) {
                            is JSONObject -> {
                                val txt = el.optString("text", el.optString("title", "")).trim()
                                val done = el.optBoolean("done", false)
                                if (txt.isEmpty()) null else (if (done) "[x] " else "[ ] ") + txt
                            }
                            is String -> el.trim().takeIf { it.isNotEmpty() }?.let { txt ->
                                if (txt.startsWith("[ ] ") || txt.startsWith("[x] ")) txt else "[ ] $txt"
                            }
                            else -> null
                        }
                    }.joinToString("\n")
                } else {
                    val rawContent = o.optString("content", o.optString("text", "")).trim()
                    if (isChecklist && rawContent.isNotEmpty()) {
                        rawContent.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") { line ->
                            if (line.startsWith("[ ] ") || line.startsWith("[x] ")) line
                            else "[ ] " + line.removePrefix("- [ ] ").removePrefix("- ").removePrefix("* ")
                        }
                    } else rawContent
                }
                if (title.isEmpty() && content.isEmpty()) continue
                val colorIdx = parseFlexibleNoteColor(o.opt("color"))
                parsedNotes.add(
                    AiNoteDraft(
                        title = title,
                        content = content,
                        colorIndex = colorIdx,
                        pinned = o.optBoolean("pinned", false),
                        isChecklist = isChecklist,
                        hidden = o.optBoolean("hidden", false)
                    )
                )
            }

            AiPlanDraft(parsedTasks, parsedNotes).takeIf { !it.isEmpty }
        }.getOrNull()

        internal fun extractJsonObjectOrArray(raw: String): String? {
            val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            val fenced = fenceRegex.find(raw)?.groupValues?.getOrNull(1)?.trim()
            val target = fenced ?: raw
            val objStart = target.indexOf('{')
            val objEnd = target.lastIndexOf('}')
            if (objStart >= 0 && objEnd > objStart) {
                return target.substring(objStart, objEnd + 1)
            }
            val arrStart = target.indexOf('[')
            val arrEnd = target.lastIndexOf(']')
            if (arrStart >= 0 && arrEnd > arrStart) {
                return target.substring(arrStart, arrEnd + 1)
            }
            return null
        }

        internal fun parseFlexibleDate(v: Any?, today: LocalDate): Long? {
            if (v == null) return null
            if (v is Number) {
                val n = v.toLong()
                return if (n in 10_000L..60_000L) n else null
            }
            val s = v.toString().trim().lowercase()
            if (s.isEmpty() || s == "null" || s == "someday" || s == "none") return null
            return when {
                s == "today" -> today.toEpochDay()
                s == "tomorrow" -> today.plusDays(1).toEpochDay()
                s == "yesterday" -> today.minusDays(1).toEpochDay()
                s.startsWith("+") && s.endsWith("d") -> s.removePrefix("+").removeSuffix("d").toLongOrNull()?.let { today.plusDays(it).toEpochDay() }
                else -> runCatching { LocalDate.parse(s.take(10)).toEpochDay() }.getOrNull()
            }
        }

        internal fun parseFlexibleTime(v: Any?): Int? {
            if (v == null) return null
            if (v is Number) {
                val n = v.toInt()
                return if (n in 0..1439) n else null
            }
            val s = v.toString().trim().uppercase()
            if (s.isEmpty() || s == "NULL" || s == "ALL DAY" || s == "ALL-DAY" || s == "NONE") return null
            val isPm = s.endsWith("PM")
            val isAm = s.endsWith("AM")
            val clean = s.removeSuffix("PM").removeSuffix("AM").trim()
            val parts = clean.split(":")
            if (parts.size < 2) return null
            var h = parts[0].trim().toIntOrNull() ?: return null
            val m = parts[1].trim().take(2).toIntOrNull() ?: return null
            if (isPm && h in 1..11) h += 12
            if (isAm && h == 12) h = 0
            return if (h in 0..23 && m in 0..59) h * 60 + m else null
        }

        internal fun parseFlexiblePriority(v: Any?): Int {
            if (v == null) return 0
            if (v is Number) return v.toInt().coerceIn(0, 3)
            return when (v.toString().trim().lowercase()) {
                "high", "urgent", "3", "p1" -> 3
                "medium", "med", "normal", "2", "p2" -> 2
                "low", "1", "p3" -> 1
                else -> 0
            }
        }

        internal fun parseFlexibleNoteColor(v: Any?): Int {
            if (v == null) return 0
            if (v is Number) return v.toInt().coerceIn(0, 7)
            return when (v.toString().trim().lowercase()) {
                "lavender", "purple", "1" -> 1
                "peach", "orange", "coral", "2" -> 2
                "mint", "teal", "3" -> 3
                "sky", "blue", "4" -> 4
                "butter", "yellow", "5" -> 5
                "rose", "pink", "6" -> 6
                "sage", "green", "7" -> 7
                else -> 0
            }
        }
    }
}
