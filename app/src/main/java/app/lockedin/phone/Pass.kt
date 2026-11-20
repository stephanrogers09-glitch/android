package app.lockedin.phone

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plus access on this device.
 *
 * The app cannot grant Plus to itself. Plus is granted on the server by an admin (backend
 * migration `admin_grant_plus`, callable only with the service role). The device stores the
 * expiry the server confirmed and checks it locally, so the Focus Engine keeps working offline
 * until that expiry.
 *
 * The public redemption codes are removed. Passes already stored from earlier codes are honoured
 * until they expire, so no customer who paid loses access. They cannot be renewed with codes.
 */
object Pass {
    private const val FILE = "locked-in-setup"
    private const val UNTIL = "plusUntil"

    fun owns(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        return until(context) > now
    }

    fun until(context: Context): Long {
        return prefs(context).getLong(UNTIL, 0L)
    }

    fun untilLabel(context: Context): String {
        val format = SimpleDateFormat("d MMM yyyy", Locale.US)
        return format.format(Date(until(context)))
    }

    /**
     * Stores the expiry the server confirmed. Only the server sync may call this, and it is not
     * wired up yet. The server value replaces the local one, even when earlier, so revocations
     * take effect.
     */
    internal fun applyServerExpiry(context: Context, untilMs: Long) {
        prefs(context).edit().putLong(UNTIL, untilMs).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
