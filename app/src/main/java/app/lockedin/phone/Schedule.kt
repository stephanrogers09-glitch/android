package app.lockedin.phone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Schedule {
    fun arm(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pending(context)
        val plan = SessionStore.plan(context)
        if (!plan.on || plan.line.isBlank() || !Pass.owns(context)) {
            alarm.cancel(pending)
            return
        }
        val whenMs = next(plan.hour, plan.minute)
        if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending)
        }
    }

    fun needsExactPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return !alarm.canScheduleExactAlarms()
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            42,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun next(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        if (cal.timeInMillis <= System.currentTimeMillis() + 30_000L) cal.add(Calendar.DATE, 1)
        return cal.timeInMillis
    }
}
