package app.childrensimulator.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.childrensimulator.MainActivity
import app.childrensimulator.R
import app.childrensimulator.sim.engine.AlertWake
import app.childrensimulator.sim.engine.Channels
import app.childrensimulator.sim.engine.NotificationOp

/** Posts and cancels notifications for the simulation's [NotificationOp]s (docs/05 §5, docs/11 §6). */
class Notifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val care = NotificationChannel(Channels.CARE, context.getString(R.string.channel_care), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_care_desc)
        }
        // Name-based URI: numeric resource IDs can change between builds, and a channel's sound is fixed once created.
        val nightSound = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/night_cry")
        val night = NotificationChannel(Channels.NIGHT, context.getString(R.string.channel_night), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_night_desc)
            setSound(nightSound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 250, 400)
        }
        val summary = NotificationChannel(Channels.SUMMARY, context.getString(R.string.channel_summary), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_summary_desc)
        }
        manager.createNotificationChannels(listOf(care, night, summary))
    }

    fun permissionGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notificationsEnabled(): Boolean = nm.areNotificationsEnabled()

    fun channelEnabled(id: String): Boolean {
        val ch = context.getSystemService(NotificationManager::class.java).getNotificationChannel(id) ?: return false
        return ch.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun execute(ops: List<NotificationOp>) {
        for (op in ops) {
            when (op) {
                is NotificationOp.Post -> post(op)
                is NotificationOp.Cancel -> nm.cancel(idFor(op.groupKey))
                is NotificationOp.Summary -> summary(op.key, op.title, op.text)
            }
        }
    }

    fun cancelAll() = nm.cancelAll()

    private fun idFor(key: String) = 1000 + (key.hashCode() and 0x0fffffff)

    private fun openIntent(groupKey: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (groupKey != null) putExtra(MainActivity.EXTRA_GROUP, groupKey)
        }
        return PendingIntent.getActivity(context, idFor(groupKey ?: "open"), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(action: String, groupKey: String): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).setAction(action).putExtra(MainActivity.EXTRA_GROUP, groupKey)
        return PendingIntent.getBroadcast(context, idFor(action + groupKey), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("MissingPermission")
    private fun post(op: NotificationOp.Post) {
        if (!permissionGranted()) return
        val public = NotificationCompat.Builder(context, op.channel)
            .setSmallIcon(R.drawable.ic_stat_baby)
            .setContentTitle("Children Simulator")
            .setContentText("Virtual baby care")
            .build()
        val b = NotificationCompat.Builder(context, op.channel)
            .setSmallIcon(R.drawable.ic_stat_baby)
            .setContentTitle(op.title)
            .setContentText(op.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(op.text))
            .setContentIntent(openIntent(op.groupKey))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(op.silent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setPriority(if (op.channel == Channels.NIGHT) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        if (op.nightActions) {
            b.addAction(0, "Care now", openIntent(op.groupKey))
            b.addAction(0, "Silence", actionIntent(ActionReceiver.ACTION_SILENCE, op.groupKey))
            b.addAction(0, "Hand off the night", actionIntent(ActionReceiver.ACTION_HANDOFF_NIGHT, op.groupKey))
        } else {
            b.addAction(0, "Hand off", actionIntent(ActionReceiver.ACTION_HANDOFF, op.groupKey))
        }
        nm.notify(idFor(op.groupKey), b.build())
    }

    @Suppress("MissingPermission")
    private fun summary(key: String, title: String, text: String) {
        if (!permissionGranted()) return
        val b = NotificationCompat.Builder(context, Channels.SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_baby)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent(null))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        nm.notify(idFor("summary_$key"), b.build())
    }

    /** One-shot channel test from onboarding and settings (docs/05 §3 step 1). */
    @Suppress("MissingPermission")
    fun test(channel: String) {
        if (!permissionGranted()) return
        val night = channel == Channels.NIGHT
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_baby)
            .setContentTitle(if (night) "Test · Night care" else "Test · Baby care")
            .setContentText(if (night) "This is how a night wake-up will sound on this phone." else "This is how a care reminder will look.")
            .setContentIntent(openIntent(null))
            .setAutoCancel(true)
            .setPriority(if (night) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        nm.notify(idFor("test_$channel"), b.build())
    }
}

/** Schedules the single next OS wake-up (docs/11 §6.1-6.2). Uses elapsed time so clock changes don't shift it. */
class AlarmScheduler(private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)

    private fun pending(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_WAKE)
        return PendingIntent.getBroadcast(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    /**
     * Returns a short description of how the wake-up was scheduled (shown in Settings).
     *
     * Inexact alarms may arrive up to 75% of their delay late. To keep a routine reminder close to its moment
     * without exact-alarm access, we request a shorter delay (target / 1.75) so even a late delivery lands before
     * the target; the receiver then re-plans with the remaining, smaller delay. The last step is under two
     * minutes, so the reminder is at most about 90 seconds late (docs/11 §6.2).
     */
    fun schedule(wake: AlertWake, nowTrusted: Long, allowExact: Boolean): String {
        val target = (wake.atWall - nowTrusted).coerceAtLeast(1_000L)
        val pi = pending()
        val exact = wake.exact && allowExact && canExact()
        val delay = if (exact || target <= 2 * 60_000L) target else (target / 1.75).toLong().coerceAtLeast(60_000L)
        val at = SystemClock.elapsedRealtime() + delay
        return try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
                "exact"
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
                if (delay < target) "inexact, re-checking in ${delay / 60_000} min" else "inexact"
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            "inexact (exact access revoked)"
        }
    }

    fun cancel() = am.cancel(pending())
}
