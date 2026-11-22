package com.lokesh.tidy.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lokesh.tidy.MainActivity
import com.lokesh.tidy.R
import com.lokesh.tidy.data.Repository
import com.lokesh.tidy.data.Subtask
import com.lokesh.tidy.data.Task
import com.lokesh.tidy.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "reminders"
    const val EXTRA_ID = "task_id"
    const val ACTION_FIRE = "com.lokesh.tidy.REMIND"
    const val ACTION_DONE = "com.lokesh.tidy.DONE"
    const val ACTION_SNOOZE = "com.lokesh.tidy.SNOOZE"
    const val ACTION_SNOOZE_1H = "com.lokesh.tidy.SNOOZE_1H"
    const val ACTION_MIDNIGHT = "com.lokesh.tidy.MIDNIGHT"
    const val ACTION_TEST = "com.lokesh.tidy.TEST_REMIND"

    fun dueTimeMillis(t: Task): Long? {
        val date = t.dueDate ?: return null
        val minutes = t.dueTime ?: (9 * 60)
        val ldt = LocalDate.ofEpochDay(date).atStartOfDay().plusMinutes(minutes.toLong())
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun triggerTime(t: Task): Long? {
        val due = dueTimeMillis(t) ?: return null
        val offset = t.reminderOffset ?: return null
        return due - offset * 60_000L
    }

    private fun pending(context: Context, id: Long, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? {
        val i = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id.toInt(), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, t: Task) {
        cancel(context, t.id)
        if (t.completed) return
        val rawAt = triggerTime(t) ?: return
        val dueAt = dueTimeMillis(t) ?: rawAt
        val now = System.currentTimeMillis()
        // Handle same-minute scheduling and "offset already passed, but due time still ahead"
        val effectiveAt = when {
            rawAt > now -> rawAt
            rawAt >= now - 60_000L -> now + 2_000L
            dueAt > now -> dueAt
            dueAt >= now - 60_000L -> now + 2_000L
            else -> return
        }
        scheduleAt(context, t, effectiveAt)
    }

    fun scheduleAt(context: Context, t: Task, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, t.id) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, id: Long) {
        val pi = pending(context, id, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    fun cancelAll(context: Context, ids: List<Long>) = ids.forEach { cancel(context, it) }

    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION_MIDNIGHT)
        val pi = PendingIntent.getBroadcast(context, -1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = LocalDate.now().plusDays(1).atTime(LocalTime.of(0, 0, 30)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for your to-dos"
                enableVibration(true)
            })
        }
    }

    fun scheduleTest(context: Context, delayMs: Long = 5_000L) {
        ensureChannel(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val i = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_TEST)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val pi = PendingIntent.getBroadcast(context, -99, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + delayMs
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun show(context: Context, t: Task, subtasks: List<Subtask> = emptyList()) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(context, t.id.toInt(), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(a: String) = PendingIntent.getBroadcast(context, (t.id * 10 + a.hashCode()).toInt(), Intent(context, ReminderReceiver::class.java).setAction(a).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra(EXTRA_ID, t.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val sortedSubs = subtasks.sortedBy { it.sortOrder }
        val shortText = when {
            t.notes.isNotBlank() -> t.notes
            sortedSubs.isNotEmpty() -> "${sortedSubs.count { !it.done }} of ${sortedSubs.size} steps remaining"
            else -> "Time to get it done ✨"
        }
        val bigText = if (sortedSubs.isNotEmpty()) {
            val subLines = sortedSubs.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.title }
            if (t.notes.isNotBlank()) "${t.notes}\n\n$subLines" else subLines
        } else {
            shortText
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tidy)
            .setColor(0xFF7C6CF2.toInt())
            .setContentTitle(t.title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "✓ Done", action(ACTION_DONE))
            .addAction(0, "Snooze 10m", action(ACTION_SNOOZE))
            .addAction(0, "+1 hour", action(ACTION_SNOOZE_1H))
            .build()
        try { NotificationManagerCompat.from(context).notify(t.id.toInt(), n) } catch (_: SecurityException) {}
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val repo = Repository.get(context)
        val id = intent.getLongExtra(Reminders.EXTRA_ID, -1)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_FIRE -> repo.dao.task(id)?.takeIf { !it.task.completed }?.let { Reminders.show(context, it.task, it.subtasks) }
                    Reminders.ACTION_DONE -> { NotificationManagerCompat.from(context).cancel(id.toInt()); repo.setCompleted(id, true) }
                    Reminders.ACTION_SNOOZE -> { NotificationManagerCompat.from(context).cancel(id.toInt()); repo.snooze(id, 10) }
                    Reminders.ACTION_SNOOZE_1H -> { NotificationManagerCompat.from(context).cancel(id.toInt()); repo.snooze(id, 60) }
                    Reminders.ACTION_MIDNIGHT -> { WidgetUpdater.updateAll(context); Reminders.scheduleMidnight(context) }
                    Reminders.ACTION_TEST -> Reminders.show(
                        context,
                        Task(id = 999999L, title = "Tidy reminder works ✨", notes = "Alarms are firing properly on your device.")
                    )
                }
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repository.get(context)
                repo.dao.allTasks().forEach { Reminders.schedule(context, it.task) }
                Reminders.scheduleMidnight(context)
                WidgetUpdater.updateAll(context)
            } finally { pending.finish() }
        }
    }
}
