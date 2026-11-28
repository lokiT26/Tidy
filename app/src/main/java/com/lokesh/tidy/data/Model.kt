package com.lokesh.tidy.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val emoji: String = "",
    val sortOrder: Int = 0,
)

@Entity(tableName = "tasks", indices = [Index("categoryId"), Index("dueDate")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** Epoch day (LocalDate.toEpochDay) or null for "someday". */
    val dueDate: Long? = null,
    /** Minutes from midnight, or null for all-day. */
    val dueTime: Int? = null,
    /** Minutes before due time to remind; null = no reminder. */
    val reminderOffset: Int? = null,
    /** 0 = none, 1 = low, 2 = medium, 3 = high */
    val priority: Int = 0,
    val categoryId: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    /** NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY */
    val repeat: String = "NONE",
    /** ID of the next recurring occurrence spawned when this task was completed. */
    val spawnedTaskId: Long? = null,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")]
)
data class Subtask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val colorIndex: Int = 0,
    val pinned: Boolean = false,
    val isChecklist: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
)

data class TaskWithSubtasks(
    @Embedded val task: Task,
    @Relation(parentColumn = "id", entityColumn = "taskId") val subtasks: List<Subtask>,
)

@Dao
interface TidyDao {
    // Tasks
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY completed ASC, sortOrder ASC, createdAt DESC")
    fun observeTasks(): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY completed ASC, sortOrder ASC, createdAt DESC")
    suspend fun allTasks(): List<TaskWithSubtasks>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Long): TaskWithSubtasks?

    @Query("SELECT * FROM tasks WHERE title = :title AND repeat = :repeat AND dueDate = :dueDate AND completed = 0 LIMIT 1")
    suspend fun findRecurringInstance(title: String, repeat: String, dueDate: Long): Task?

    @Insert suspend fun insertTask(t: Task): Long
    @Update suspend fun updateTask(t: Task)
    @Update suspend fun updateTasks(t: List<Task>)
    @Delete suspend fun deleteTask(t: Task)
    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM tasks") suspend fun minSort(): Int

    // Subtasks
    @Insert suspend fun insertSubtasks(s: List<Subtask>)
    @Update suspend fun updateSubtask(s: Subtask)
    @Query("DELETE FROM subtasks WHERE taskId = :taskId") suspend fun deleteSubtasksOf(taskId: Long)

    // Categories
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") fun observeCategories(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") suspend fun allCategories(): List<Category>
    @Insert suspend fun insertCategory(c: Category): Long
    @Update suspend fun updateCategory(c: Category)
    @Update suspend fun updateCategories(c: List<Category>)
    @Delete suspend fun deleteCategory(c: Category)
    @Query("UPDATE tasks SET categoryId = NULL WHERE categoryId = :id") suspend fun clearCategory(id: Long)

    // Notes
    @Query("SELECT * FROM notes ORDER BY pinned DESC, sortOrder ASC, updatedAt DESC") fun observeNotes(): Flow<List<Note>>
    @Query("SELECT * FROM notes ORDER BY pinned DESC, sortOrder ASC, updatedAt DESC") suspend fun allNotes(): List<Note>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun note(id: Long): Note?
    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM notes") suspend fun minNoteSort(): Int
    @Insert suspend fun insertNote(n: Note): Long
    @Update suspend fun updateNote(n: Note)
    @Update suspend fun updateNotes(n: List<Note>)
    @Delete suspend fun deleteNote(n: Note)

    // Bulk (backup)
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("SELECT * FROM subtasks") suspend fun allSubtasks(): List<Subtask>
    @Insert suspend fun insertTasksRaw(t: List<Task>)
    @Insert suspend fun insertNotesRaw(n: List<Note>)
    @Insert suspend fun insertCategoriesRaw(c: List<Category>)
}

@Database(entities = [Category::class, Task::class, Subtask::class, Note::class], version = 3, exportSchema = true)
abstract class TidyDatabase : RoomDatabase() {
    abstract fun dao(): TidyDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN spawnedTaskId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE notes ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
