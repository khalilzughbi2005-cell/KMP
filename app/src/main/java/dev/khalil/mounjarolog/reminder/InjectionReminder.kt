package dev.khalil.mounjarolog.reminder

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
import dev.khalil.mounjarolog.MainActivity
import dev.khalil.mounjarolog.R
import dev.khalil.mounjarolog.data.LoggerDatabase
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.latestInjection
import dev.khalil.mounjarolog.model.suggestNextSite
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One local notification on the morning the next weekly injection is due. No network involved. */
object InjectionReminder {
    private const val CHANNEL = "injection_due"
    private const val PREFS = "reminder"
    private const val KEY_ENABLED = "enabled"
    private val TIME: LocalTime = LocalTime.of(9, 0)

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean, data: AppData) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        schedule(context, data)
    }

    fun dueDate(data: AppData): LocalDate? = data.latestInjection()?.date?.plusDays(7)

    fun schedule(context: Context, data: AppData) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        val due = dueDate(data) ?: return
        if (!isEnabled(context)) return
        val at = due.atTime(TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at <= System.currentTimeMillis()) return
        // Inexact is fine for a morning reminder and needs no exact-alarm permission.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun show(context: Context, data: AppData) {
        if (!isEnabled(context)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val last = data.latestInjection() ?: return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Spritzen-Erinnerung", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val dose = if (last.doseMg % 1.0 == 0.0) last.doseMg.toInt().toString() else last.doseMg.toString().replace('.', ',')
        val text = "Zuletzt $dose mg am " + last.date.format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMANY)) +
            " · Vorschlag: " + suggestNextSite(data.injections)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Spritze heute fällig")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(1, notification)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val data = LoggerDatabase(context).use { it.loadAll() }
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) InjectionReminder.schedule(context, data)
                else InjectionReminder.show(context, data)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
