package app.lockedin.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val plan = SessionStore.plan(context)
        if (plan.on && plan.line.isNotBlank() && Pass.owns(context) && !SessionStore.isActive(context)) {
            SessionStore.markSetup(context)
            SessionStore.start(context, plan.preset, plan.line)
            bringUp(context)
        }
        Schedule.arm(context)
    }

    private fun bringUp(context: Context) {
        val launch = Intent(context, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            7,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("lock", "Lock", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            manager.createNotificationChannel(channel)
        }
        val note = Notification.Builder(context, "lock")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Locked in")
            .setContentText("The hour has started.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(7, note)
        try {
            context.startActivity(launch)
        } catch (_: Exception) {
        }
    }
}
