package app.lockedin.core

import app.lockedin.core.focus.FocusClock

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

fun main() {
    check("remaining counts down", FocusClock.remainingMs(1000L, 400L) == 600L)
    check("remaining is never negative", FocusClock.remainingMs(1000L, 2000L) == 0L)

    check("progress starts at zero", FocusClock.progress(1000L, 3000L, 1000L) == 0.0)
    check("progress is halfway", FocusClock.progress(1000L, 3000L, 2000L) == 0.5)
    check("progress is complete at the end", FocusClock.progress(1000L, 3000L, 3000L) == 1.0)
    check("progress is complete past the end", FocusClock.progress(1000L, 3000L, 9000L) == 1.0)
    check("progress is zero before the start", FocusClock.progress(1000L, 3000L, 500L) == 0.0)
    check("unknown start reads as zero until the end", FocusClock.progress(0L, 3000L, 2000L) == 0.0)
    check("an empty span reads as zero until the end", FocusClock.progress(3000L, 3000L, 2000L) == 0.0)

    check("countdown zero", FocusClock.countdown(0L) == "00:00")
    check("countdown rounds a millisecond up", FocusClock.countdown(1L) == "00:01")
    check("countdown one minute", FocusClock.countdown(60_000L) == "01:00")
    check("countdown twenty-five minutes", FocusClock.countdown(25L * 60_000L) == "25:00")
    check("countdown under an hour keeps minutes", FocusClock.countdown(59L * 60_000L + 59_000L) == "59:59")
    check("countdown from an hour shows hours", FocusClock.countdown(3_600_000L) == "1:00:00")
    check("countdown with hours minutes and seconds", FocusClock.countdown(3_754_000L) == "1:02:34")
    check("negative countdown is zero", FocusClock.countdown(-5_000L) == "00:00")

    check("minutes read as minutes", FocusClock.durationText(25) == "25 minutes")
    check("one minute is singular", FocusClock.durationText(1) == "1 minute")
    check("whole hour is singular", FocusClock.durationText(60) == "1 hour")
    check("two hours", FocusClock.durationText(120) == "2 hours")
    check("hour and a half", FocusClock.durationText(90) == "1 hour 30 minutes")
    check("three hours", FocusClock.durationText(180) == "3 hours")
    check("open-ended is until morning", FocusClock.durationText(null) == "Until morning")

    println("focus clock checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed focus clock check(s) failed")
}
