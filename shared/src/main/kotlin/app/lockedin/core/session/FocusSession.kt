package app.lockedin.core.session

/** One focus session as recorded by the app. Times are epoch milliseconds (UTC). */
data class FocusSession(
    val id: String,
    val preset: Preset,
    val intention: String,
    val startedAtMs: Long,
    val endsAtMs: Long,
) {
    fun isActive(nowMs: Long): Boolean = nowMs < endsAtMs

    fun remainingMs(nowMs: Long): Long = (endsAtMs - nowMs).coerceAtLeast(0L)

    companion object {
        /** Matches the SetupActivity line field limit on Android. */
        const val MAX_INTENTION_CHARS = 80
        /** Matches the Android default line when the field is left blank. */
        const val DEFAULT_INTENTION = "Do it."

        fun start(
            id: String,
            preset: Preset,
            rawIntention: String,
            startedAtMs: Long,
            localOffsetMs: Long = 0L,
        ): FocusSession = FocusSession(
            id = id,
            preset = preset,
            intention = normaliseIntention(rawIntention),
            startedAtMs = startedAtMs,
            endsAtMs = SessionClock.endAt(preset, startedAtMs, localOffsetMs),
        )

        fun normaliseIntention(raw: String): String {
            val trimmed = raw.trim().take(MAX_INTENTION_CHARS)
            return if (trimmed.isBlank()) DEFAULT_INTENTION else trimmed
        }

        /** Countdown as HH:MM:SS, same format as the Android lock screen. */
        fun formatCountdown(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0L)
            val hours = total / 3600
            val minutes = (total % 3600) / 60
            val seconds = total % 60
            return "${hours.pad()}:${minutes.pad()}:${seconds.pad()}"
        }

        private fun Long.pad(): String = toString().padStart(2, '0')
    }
}
