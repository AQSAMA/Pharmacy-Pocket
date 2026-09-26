package com.aqsama.pharmacypocket.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aqsama.pharmacypocket.MainActivity
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

private const val CHANNEL_ID = "medicine-reminders"

/** Inexact alarms avoid special exact-alarm access; Android may deliver them a little late. */
object MedicineReminders {
    private fun intent(context: Context, id: String) = Intent(context, MedicineReminderReceiver::class.java).apply {
        data = Uri.parse("pharmacypocket://reminder/${Uri.encode(id)}")
        putExtra("medicineId", id)
    }

    private fun pending(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0, intent(context, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun cancel(context: Context, id: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context, id))
    }

    fun schedule(context: Context, item: Medicine) {
        val due = item.reminderAt
        if (due == null) {
            cancel(context, item.id)
            return
        }
        if (due <= System.currentTimeMillis()) return // Recovery delivers overdue reminders once.
        cancel(context, item.id)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, due, pending(context, item.id),
        )
    }

    fun nextDue(due: Long, repeat: ReminderRepeat, now: Long, monthlyDay: Int? = null): Long? {
        if (repeat == ReminderRepeat.NONE) return null
        var next = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
        val day = (monthlyDay ?: next.dayOfMonth).coerceIn(1, 31)
        while (next.toInstant().toEpochMilli() <= now) {
            next = when (repeat) {
                ReminderRepeat.DAILY -> next.plusDays(1)
                ReminderRepeat.WEEKLY -> next.plusWeeks(1)
                ReminderRepeat.MONTHLY -> {
                    val month = YearMonth.from(next).plusMonths(1)
                    next.withDayOfMonth(1).plusMonths(1).withDayOfMonth(day.coerceAtMost(month.lengthOfMonth()))
                }
                ReminderRepeat.NONE -> return null
            }
        }
        return next.toInstant().toEpochMilli()
    }

    fun notify(context: Context, item: Medicine) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Medicine reminders", NotificationManager.IMPORTANCE_DEFAULT,
        ))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(item.id.hashCode(), NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(item.name)
            .setContentText("Medicine reminder")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Pharmacy Pocket reminder")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    /** Recover alarms missed during shutdown or while Android could not deliver them. */
    fun recover(context: Context, items: List<Medicine>) {
        items.forEach { item ->
            val due = item.reminderAt ?: return@forEach
            if (due > System.currentTimeMillis()) return@forEach
            deliver(context, item)
        }
    }

    fun deliver(context: Context, item: Medicine) {
        val due = item.reminderAt ?: return
        val now = System.currentTimeMillis()
        val next = nextDue(due, item.reminderRepeat, now, item.reminderDay)
        val database = MedicineDatabase(context)
        val stillPending = try { database.advanceReminder(item.id, due, next) } finally { database.close() }
        if (!stillPending) return
        if (next != null) schedule(context, item.copy(reminderAt = next))
        notify(context, item)
    }
}

class MedicineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("medicineId") ?: return
        val pendingResult = goAsync()
        Thread {
            try {
                runBlocking {
                    val repository = PharmacyRepository(context)
                    val item = repository.loadSnapshot().items.firstOrNull { it.id == id } ?: return@runBlocking
                    val due = item.reminderAt ?: return@runBlocking
                    val now = System.currentTimeMillis()
                    if (due > now + 60_000L) {
                        MedicineReminders.schedule(context, item)
                        return@runBlocking
                    }
                    MedicineReminders.deliver(context, item)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        Thread {
            try {
                runBlocking {
                    val items = PharmacyRepository(context).loadSnapshot().items
                    MedicineReminders.recover(context, items)
                    PharmacyRepository(context).loadSnapshot().items.forEach { MedicineReminders.schedule(context, it) }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
