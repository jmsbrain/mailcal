package com.jmsbrain.mailcal

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.ZoneId

/**
 * Posts a notification when an event's reminder (VALARM) is due, for events in the offline copy.
 * One alarm is set at a time, for the next reminder; when it goes off, the one after is set.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val ACTION_FIRE = "com.jmsbrain.mailcal.REMINDER"

    /** Reminders older than this when the alarm finally runs are skipped. */
    private const val STALE_MS = 6 * 60 * 60_000L

    private class Due(val at: Long, val item: Item)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    private fun upcoming(items: List<Item>): List<Due> =
        items.flatMap { item -> item.occ.alarms.map { Due(it.toEpochMilli(), item) } }.sortedBy { it.at }

    /** Sets the alarm for the next reminder after the last one shown. */
    fun schedule(ctx: Context, items: List<Item>) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        // First run: don't replay reminders from before the app was installed.
        if (!p.contains("last")) p.edit().putLong("last", now).apply()
        val last = p.getLong("last", now)
        val next = upcoming(items).firstOrNull { it.at > last }
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePending(ctx)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi)
    }

    /** Shows every reminder that's come due since last time, then sets the next alarm. */
    fun fire(ctx: Context) {
        Repo.init(ctx.applicationContext)
        val items = Repo.state.value.items
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val last = p.getLong("last", now - 60_000)
        upcoming(items)
            .filter { it.at > last && it.at <= now + 1000 && it.at > now - STALE_MS }
            .forEach { notify(ctx, it) }
        p.edit().putLong("last", maxOf(last, now + 1000)).apply()
        schedule(ctx, items)
    }

    private fun notify(ctx: Context, due: Due) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        val o = due.item.occ
        val zone = ZoneId.systemDefault()
        val text = listOf(whenText(o, zone), o.location.lineSequence().firstOrNull().orEmpty())
            .filter { it.isNotBlank() }.joinToString(" · ")
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(o.title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(o.start.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify((o.uid + o.start.toInstant() + due.at).hashCode(), n)
    }

    private fun firePending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

}

/** The reminder alarm, plus reboot and app update (alarms are cleared by both). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Repo.init(context.applicationContext)
                Reminders.schedule(context, Repo.state.value.items)
            }
            else -> Reminders.fire(context)
        }
    }
}
