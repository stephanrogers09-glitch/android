package app.lockedin.core.focus

/** Time maths for a focus session, shared by every platform so the numbers always agree. */
object FocusClock {
    /** Milliseconds left until the end. Never negative. */
    fun remainingMs(endAt: Long, now: Long): Long = (endAt - now).coerceAtLeast(0L)

    /** Share of the session already elapsed, from 0.0 to 1.0. Past the end is always 1.0. */
    fun progress(startAt: Long, endAt: Long, now: Long): Double {
        if (now >= endAt) return 1.0
        if (startAt <= 0L || endAt <= startAt) return 0.0
        return ((now - startAt).toDouble() / (endAt - startAt).toDouble()).coerceIn(0.0, 1.0)
    }

    /** "38:14" under an hour, "1:02:34" from an hour. Seconds round up, so 0:00 shows only at the end. */
    fun countdown(remainingMs: Long): String {
        val total = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) "$hours:${pad(minutes)}:${pad(seconds)}" else "${pad(minutes)}:${pad(seconds)}"
    }

    /** A session length for people to read: "25 minutes", "1 hour", "1 hour 30 minutes", "Until morning". */
    fun durationText(minutes: Int?): String {
        if (minutes == null) return "Until morning"
        val hours = minutes / 60
        val rest = minutes % 60
        val hourText = if (hours == 1) "1 hour" else "$hours hours"
        val minuteText = if (rest == 1) "1 minute" else "$rest minutes"
        return when {
            hours == 0 -> minuteText
            rest == 0 -> hourText
            else -> "$hourText $minuteText"
        }
    }

    private fun pad(value: Long): String = value.toString().padStart(2, '0')
}
