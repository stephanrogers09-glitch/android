package app.lockedin.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager

object Ping {
    private const val FILE = "locked-in-setup"
    private const val LAST = "lastPing"

    fun quit(context: Context) {
        if (!SessionStore.isActive(context)) return
        val number = SessionStore.partner(context)
        if (number.length < 8) return
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST, 0L) < 60_000L) return
        prefs.edit().putLong(LAST, now).apply()
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java) ?: return
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendTextMessage(
                number,
                null,
                "Locked In: the lock was turned off before the time ended.",
                null,
                null
            )
        } catch (_: Exception) {
        }
    }
}
