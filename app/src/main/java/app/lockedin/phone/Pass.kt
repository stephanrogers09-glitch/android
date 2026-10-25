package app.lockedin.phone

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Pass {
    private const val FILE = "locked-in-setup"
    private const val UNTIL = "plusUntil"
    private val month = setOf(
        "7K2MQA", "4NP8LD", "M3C6VR", "H8T2YK", "Q1W5BN",
        "D9F4XE", "R6A3UJ", "P2L7SC", "V5G8HM", "C4Y1KT"
    )
    private val year = setOf(
        "9QH3WT", "2ZB6NF", "8ME5QA", "J7D1RP", "T3K9WL",
        "F6N2YC", "A8S4HV", "W1B7GM", "E5R3QD", "L9C2XK",
        "OWNER26"
    )

    fun owns(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        return until(context) > now
    }

    fun until(context: Context): Long {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(UNTIL, 0L)
    }

    fun untilLabel(context: Context): String {
        val format = SimpleDateFormat("d MMM yyyy", Locale.US)
        return format.format(Date(until(context)))
    }

    fun grant(context: Context, days: Int) {
        val start = maxOf(until(context), System.currentTimeMillis())
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(UNTIL, start + days * 86_400_000L)
            .apply()
    }

    fun redeem(context: Context, raw: String): Boolean {
        val code = raw.uppercase(Locale.US).filter { it.isLetterOrDigit() }.removePrefix("LI")
        val days = when {
            code in month -> 31
            code in year -> 365
            else -> return false
        }
        val start = maxOf(until(context), System.currentTimeMillis())
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(UNTIL, start + days * 86_400_000L)
            .apply()
        return true
    }
}
