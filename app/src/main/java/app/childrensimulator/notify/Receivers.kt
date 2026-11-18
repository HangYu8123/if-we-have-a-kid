package app.childrensimulator.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.childrensimulator.ChildSimApp
import app.childrensimulator.MainActivity
import app.childrensimulator.sim.engine.Command
import app.childrensimulator.sim.engine.NotificationOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun BroadcastReceiver.work(context: Context, block: suspend (ChildSimApp) -> Unit) {
    val pending = goAsync()
    val app = context.applicationContext as ChildSimApp
    receiverScope.launch {
        try {
            block(app)
        } finally {
            pending.finish()
        }
    }
}

/** The scheduled wake-up: advance, post only still-actionable alerts, and plan the next one (docs/11 §6.1 step 3). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WAKE) return
        work(context) { it.container.repository.onAlarm() }
    }

    companion object {
        const val ACTION_WAKE = "app.childrensimulator.WAKE"
    }
}

/** Notification actions: Silence, Hand off and Hand off the night (docs/05 §3 step 4). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val group = intent.getStringExtra(MainActivity.EXTRA_GROUP) ?: return
        work(context) { app ->
            val repo = app.container.repository
            when (intent.action) {
                ACTION_SILENCE -> repo.dispatch(Command.SilenceAlert(group))
                ACTION_HANDOFF -> repo.dispatch(Command.HandOffNeeds)
                ACTION_HANDOFF_NIGHT -> repo.dispatch(Command.HandOffNight)
            }
            app.container.notifier.execute(listOf(NotificationOp.Cancel(group)))
        }
    }

    companion object {
        const val ACTION_SILENCE = "app.childrensimulator.SILENCE"
        const val ACTION_HANDOFF = "app.childrensimulator.HANDOFF"
        const val ACTION_HANDOFF_NIGHT = "app.childrensimulator.HANDOFF_NIGHT"
    }
}

/** Reschedules after reboot, app update, clock or zone changes, and exact-alarm access changes (docs/11 §6.3). */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> work(context) { it.container.repository.onAlarm() }
        }
    }
}
