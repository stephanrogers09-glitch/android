package app.lockedin.core.session

/**
 * Focus lengths. Mirrors the presets used by the Android v1.8.0 Focus Engine (Session.kt)
 * so that behaviour is identical across platforms.
 *
 * [minutes] == null means "until morning": the session ends at the next 07:00 local time.
 */
enum class Preset(val id: String, val minutes: Int?) {
    M2("2", 2),
    M5("5", 5),
    M25("25", 25),
    M50("50", 50),
    M90("90", 90),
    H3("3h", 180),
    MORNING("morning", null);

    /**
     * Longer locks and until-morning need Plus. Same rule as the Android `Preset.paid`.
     * Free access is capped at 25 minutes.
     */
    val requiresPlus: Boolean
        get() = (minutes ?: (FREE_MAX_MINUTES + 1)) > FREE_MAX_MINUTES

    companion object {
        const val FREE_MAX_MINUTES = 25
        const val MORNING_HOUR = 7

        /** Same fallback as Android `Preset.fromId`. */
        fun fromId(id: String?): Preset = entries.firstOrNull { it.id == id } ?: M50
    }
}

/** Pure time arithmetic. No platform clocks: callers pass the time and the device's UTC offset. */
object SessionClock {
    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 24L * 60L * MINUTE_MS

    /**
     * When a session started at [startMs] should end.
     * [localOffsetMs] is the device's offset from UTC in milliseconds (Kampala is +3h = 10_800_000).
     *
     * Note: the morning calculation uses a fixed offset and does not model daylight-saving
     * changes. Android's own Calendar-based logic does; Kampala has no DST, so this is exact there.
     */
    fun endAt(preset: Preset, startMs: Long, localOffsetMs: Long = 0L): Long {
        val minutes = preset.minutes
        if (minutes != null) return startMs + minutes * MINUTE_MS

        val localNow = startMs + localOffsetMs
        val localDayStart = floorDiv(localNow, DAY_MS) * DAY_MS
        var target = localDayStart + Preset.MORNING_HOUR * 60L * MINUTE_MS
        if (target <= localNow) target += DAY_MS
        return target - localOffsetMs
    }

    /** Floor division that is correct for negative numbers (Math.floorDiv is not in common Kotlin). */
    internal fun floorDiv(a: Long, b: Long): Long = if (a >= 0) a / b else (a - b + 1) / b
}
