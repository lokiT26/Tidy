package com.lokesh.tidy

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.lokesh.tidy.data.Repository
import com.lokesh.tidy.data.Task
import com.lokesh.tidy.reminder.Reminders
import com.lokesh.tidy.ui.Dates
import com.lokesh.tidy.ui.screens.CheckItem
import com.lokesh.tidy.ui.screens.applyChecklistValueChange
import com.lokesh.tidy.ui.screens.makeEditableCheckItem
import com.lokesh.tidy.ui.screens.parseChecklist
import com.lokesh.tidy.ui.screens.serializeChecklist
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class TidyLogicTest {
    private val zone = ZoneId.of("UTC")
    private fun completedAt(date: LocalDate, hour: Int = 10): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    // ---------- Recurring tasks ----------

    @Test
    fun nextOccurrence_dailyAndWeekly() {
        val wed = LocalDate.of(2026, 11, 18)
        assertEquals(LocalDate.of(2026, 11, 19), Repository.nextOccurrence(wed, "DAILY", wed))
        assertEquals(LocalDate.of(2026, 11, 25), Repository.nextOccurrence(wed, "WEEKLY", wed))
    }

    @Test
    fun nextOccurrence_weekdaysSkipsWeekend() {
        val fri = LocalDate.of(2026, 11, 20)
        assertEquals(DayOfWeek.FRIDAY, fri.dayOfWeek)
        val next = Repository.nextOccurrence(fri, "WEEKDAYS", fri)
        assertEquals(LocalDate.of(2026, 11, 23), next)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
    }

    @Test
    fun nextOccurrence_catchesUpOverdueTasksToTodayOrFuture() {
        val old = LocalDate.of(2026, 11, 10)
        val today = LocalDate.of(2026, 11, 20)
        val nextDaily = Repository.nextOccurrence(old, "DAILY", today)
        assertFalse(nextDaily.isBefore(today))
        assertEquals(today, nextDaily)

        val nextWeekly = Repository.nextOccurrence(old, "WEEKLY", today)
        assertEquals(LocalDate.of(2026, 11, 24), nextWeekly)
    }

    @Test
    fun nextOccurrence_monthlyAndYearly() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), Repository.nextOccurrence(jan31, "MONTHLY", jan31))
        assertEquals(LocalDate.of(2027, 1, 31), Repository.nextOccurrence(jan31, "YEARLY", jan31))
    }

    // ---------- Streaks (overdue counts toward today only) ----------

    @Test
    fun streaks_emptyAndSingleToday() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals(0 to 0, Repository.streaks(emptyList(), today, zone))

        val tasks = listOf(Task(title = "A", completed = true, completedAt = completedAt(today)))
        assertEquals(1 to 1, Repository.streaks(tasks, today, zone))
    }

    @Test
    fun streaks_yesterdayOnlyStillActiveBeforeTodayCompletion() {
        val today = LocalDate.of(2026, 11, 20)
        val tasks = listOf(
            Task(title = "D1", completed = true, completedAt = completedAt(today.minusDays(2))),
            Task(title = "D2", completed = true, completedAt = completedAt(today.minusDays(1))),
        )
        assertEquals(2 to 2, Repository.streaks(tasks, today, zone))
    }

    @Test
    fun streaks_brokenIfGapBeforeYesterday() {
        val today = LocalDate.of(2026, 11, 20)
        val tasks = listOf(
            Task(title = "Old1", completed = true, completedAt = completedAt(today.minusDays(5))),
            Task(title = "Old2", completed = true, completedAt = completedAt(today.minusDays(4))),
            Task(title = "Old3", completed = true, completedAt = completedAt(today.minusDays(3))),
        )
        assertEquals(0 to 3, Repository.streaks(tasks, today, zone))
    }

    @Test
    fun streaks_overdueTaskCompletedTodayCountsTowardTodayOnly() {
        val today = LocalDate.of(2026, 11, 20)
        // Due 3 days ago, completed today -> counts for today only, not backfilling missed days
        val overdueTask = Task(
            title = "Late bill",
            dueDate = today.minusDays(3).toEpochDay(),
            completed = true,
            completedAt = completedAt(today),
        )
        assertEquals(1 to 1, Repository.streaks(listOf(overdueTask), today, zone))
    }

    // ---------- Checklist parsing & multi-line preservation ----------

    @Test
    fun checklist_serializeAndParsePreservesStatesAndMultilineItems() {
        val items = listOf(
            CheckItem(done = true, text = "Buy milk\n2 liters"),
            CheckItem(done = false, text = "Call Mom"),
            CheckItem(done = true, text = "Pack bags\nPassport\nCharger"),
        )
        val serialized = serializeChecklist(items)
        val parsed = parseChecklist(serialized)
        assertEquals(items, parsed)
    }

    @Test
    fun checklist_handlesZeroWidthMarkerWhenReturningFromSimpleText() {
        val raw = "First line\u200B\nSecond line\nThird item"
        val items = parseChecklist(raw)
        assertEquals(
            listOf(
                CheckItem(false, "First line\nSecond line"),
                CheckItem(false, "Third item"),
            ),
            items
        )
    }

    @Test
    fun checklist_blankReturnsEmpty() {
        assertTrue(parseChecklist("").isEmpty())
        assertTrue(parseChecklist("   \n  ").isEmpty())
    }

    // ---------- Reminders & Date formatting ----------

    @Test
    fun reminders_triggerTimeHandlesTimedAndAllDayTasks() {
        val day = LocalDate.of(2026, 11, 20).toEpochDay()
        val timed = Task(title = "Meeting", dueDate = day, dueTime = 14 * 60 + 30, reminderOffset = 15)
        val due = Reminders.dueTimeMillis(timed)!!
        val trigger = Reminders.triggerTime(timed)!!
        assertEquals(15 * 60_000L, due - trigger)

        val allDay = Task(title = "All day", dueDate = day, dueTime = null, reminderOffset = 0)
        assertEquals(Reminders.dueTimeMillis(allDay), Reminders.triggerTime(allDay))

        val noReminder = Task(title = "None", dueDate = day, dueTime = 600, reminderOffset = null)
        assertNull(Reminders.triggerTime(noReminder))
    }

    @Test
    fun dates_labelsAndCustomReminderFormatting() {
        assertEquals("Off", Dates.reminderLabel(null).let { if (it == "No reminder") "Off" else it })
        assertEquals("At time", Dates.reminderLabel(0))
        assertEquals("10 min before", Dates.reminderLabel(10))
        assertEquals("1 hour before", Dates.reminderLabel(60))
        assertEquals("2 hr before", Dates.reminderLabel(120))
        assertEquals("1 day before", Dates.reminderLabel(1440))
        assertEquals("3 days before", Dates.reminderLabel(4320))
        assertEquals("1h 30m before", Dates.reminderLabel(90))
        assertEquals("1d 2h 15m before", Dates.reminderLabel(1440 + 120 + 15))
        assertEquals("9:05 AM", Dates.time(9 * 60 + 5))
        assertEquals("12:00 PM", Dates.time(12 * 60))
        assertEquals("12:30 AM", Dates.time(30))
    }

    @Test
    fun checklist_backspaceOnEmptyItemMovesCursorToEndOfPreviousItem() {
        var nextId = 10L
        val items = mutableListOf(
            makeEditableCheckItem(1L, false, "First item"),
            makeEditableCheckItem(2L, false, "Second item"),
            makeEditableCheckItem(3L, false, ""),
        )
        // Simulate Backspace in empty item 3 (sentinel deleted -> empty string)
        val focusedUid = applyChecklistValueChange(items, 2, TextFieldValue("", TextRange(0))) { nextId++ }
        assertEquals(2L, focusedUid)
        assertEquals(2, items.size)
        assertEquals("Second item", items[1].cleanText)
        // Cursor must be at the very end of "Second item" (1 + length because of leading sentinel)
        assertEquals(1 + "Second item".length, items[1].tfv.selection.start)
    }

    @Test
    fun checklist_doubleEnterOnEmptyLineMovesToNewItemBelow() {
        var nextId = 10L
        val items = mutableListOf(
            makeEditableCheckItem(1L, false, "Buy milk"),
        )
        // 1st Enter at end of "Buy milk" -> adds a newline within item 0
        val afterFirstEnter = TextFieldValue("\u200BBuy milk\n", TextRange(1 + "Buy milk\n".length))
        val focus1 = applyChecklistValueChange(items, 0, afterFirstEnter) { nextId++ }
        assertNull(focus1)
        assertEquals(1, items.size)
        assertEquals("Buy milk\n", items[0].cleanText)

        // 2nd Enter on that empty line -> strips trailing newline from item 0 and creates/focuses item 1
        val afterSecondEnter = TextFieldValue("\u200BBuy milk\n\n", TextRange(1 + "Buy milk\n\n".length))
        val focus2 = applyChecklistValueChange(items, 0, afterSecondEnter) { nextId++ }
        assertEquals(10L, focus2)
        assertEquals(2, items.size)
        assertEquals("Buy milk", items[0].cleanText)
        assertEquals("", items[1].cleanText)
    }
}
