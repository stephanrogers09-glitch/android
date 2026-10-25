package app.lockedin.phone

import android.content.Context
import java.util.Calendar

enum class Preset(val id: String, val label: String, val minutes: Int?) {
    M2("2", "2 min", 2),
    M5("5", "5 min", 5),
    M25("25", "25 min", 25),
    M50("50", "50 min", 50),
    M90("90", "90 min", 90),
    H3("3h", "3 hours", 180),
    MORNING("morning", "Until morning", null);

    val paid: Boolean
        get() = (minutes ?: 26) > 25

    fun endAt(now: Long = System.currentTimeMillis()): Long {
        if (minutes != null) return now + minutes * 60_000L
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, 7)
        cal.set(Calendar.MINUTE, 0)
        if (cal.timeInMillis <= now) cal.add(Calendar.DATE, 1)
        return cal.timeInMillis
    }

    companion object {
        fun fromId(id: String?): Preset = entries.firstOrNull { it.id == id } ?: M50
    }
}

object SessionStore {
    private const val FILE = "locked-in"
    private const val END = "endsAt"
    private const val LINE = "line"
    private const val PRESET = "preset"
    private const val PERMITTED = "permitted"
    private const val SETUP_FILE = "locked-in-setup"
    private const val SETUP_DONE = "done"

    fun isActive(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        return endAt(context) > now && line(context).isNotBlank()
    }

    fun endAt(context: Context): Long {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(END, 0L)
    }

    fun line(context: Context): String {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(LINE, "").orEmpty()
    }

    fun allow(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(PERMITTED, emptySet()).orEmpty().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet(PERMITTED, current).commit()
    }

    fun isPermitted(context: Context, packageName: String): Boolean {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet(PERMITTED, emptySet())
            .orEmpty()
            .contains(packageName)
    }

    fun start(context: Context, preset: Preset, line: String, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(END, preset.endAt(now))
            .putString(LINE, line.trim())
            .putString(PRESET, preset.id)
            .putStringSet(PERMITTED, emptySet())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove(END)
            .remove(LINE)
            .remove(PRESET)
            .remove(PERMITTED)
            .apply()
    }

    fun setupDone(context: Context): Boolean {
        return context.getSharedPreferences(SETUP_FILE, Context.MODE_PRIVATE).getBoolean(SETUP_DONE, false)
    }

    fun markSetup(context: Context) {
        context.getSharedPreferences(SETUP_FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(SETUP_DONE, true)
            .apply()
    }

    fun partner(context: Context): String {
        return context.getSharedPreferences(SETUP_FILE, Context.MODE_PRIVATE)
            .getString("partner", "")
            .orEmpty()
    }

    fun plan(context: Context): Plan {
        val prefs = context.getSharedPreferences(SETUP_FILE, Context.MODE_PRIVATE)
        return Plan(
            on = prefs.getBoolean("schedOn", false),
            hour = prefs.getInt("schedHour", 22),
            minute = prefs.getInt("schedMinute", 0),
            preset = Preset.fromId(prefs.getString("schedPreset", Preset.M25.id)),
            line = prefs.getString("schedLine", "").orEmpty()
        )
    }

    fun savePlan(
        context: Context,
        on: Boolean,
        hour: Int,
        minute: Int,
        preset: Preset,
        line: String,
        partner: String
    ) {
        context.getSharedPreferences(SETUP_FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("schedOn", on)
            .putInt("schedHour", hour)
            .putInt("schedMinute", minute)
            .putString("schedPreset", preset.id)
            .putString("schedLine", line.trim())
            .putString("partner", partner.filter { it.isDigit() || it == '+' })
            .apply()
    }
}

data class Plan(
    val on: Boolean,
    val hour: Int,
    val minute: Int,
    val preset: Preset,
    val line: String
)

fun formatClock(ms: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    var hour = cal.get(Calendar.HOUR)
    if (hour == 0) hour = 12
    val minute = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "$hour:$minute"
}

fun formatBackAt(ms: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    var hour = cal.get(Calendar.HOUR)
    if (hour == 0) hour = 12
    val minute = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    val suffix = if (cal.get(Calendar.AM_PM) == Calendar.AM) "am" else "pm"
    return "$hour:$minute $suffix"
}
