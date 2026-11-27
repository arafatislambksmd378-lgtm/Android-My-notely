package com.notely.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.notely.app.MainActivity
import com.notely.app.NotelyApp
import com.notely.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Reminder"
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(ReminderScheduler.CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, noteId.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Notely reminder")
            .setContentText(label)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canPost) {
            NotificationManagerCompat.from(context).notify((noteId % 100000).toInt().let { if (it == 0) 1 else it }, notif)
        }
        // Remove the fired reminder row so the library doesn't show stale reminders.
        val app = context.applicationContext as? NotelyApp ?: return
        app.scope.launch(Dispatchers.IO) {
            runCatching {
                app.notes.setReminder(noteId, null)
                app.reminders.clearForNote(noteId)
            }
        }
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_NOTE_ID = "noteId"
    }
}

/** Re-arms reminders after a reboot or app update (AlarmManager is cleared by the system). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.rescheduleAll(context.applicationContext)
        }
    }
}

object ReminderScheduler {
    const val CHANNEL_ID = "notely_reminders"

    fun schedule(context: Context, noteId: Long, atMillis: Long, label: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderReceiver.EXTRA_LABEL, label)
        }
        val pi = PendingIntent.getBroadcast(
            context, noteId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi) }
        }
    }

    fun cancel(context: Context, noteId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, noteId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    /** Re-arms every future reminder stored in the database (after reboot or app update). */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext as? NotelyApp ?: return
        val appContext = context.applicationContext
        app.scope.launch(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                app.reminders.all().forEach { r ->
                    if (r.atMillis > now) {
                        schedule(appContext, r.noteId, r.atMillis, r.label)
                    } else {
                        // Past reminders are stale — drop them instead of firing late.
                        app.reminders.delete(r.id)
                    }
                }
            }
        }
    }
}
