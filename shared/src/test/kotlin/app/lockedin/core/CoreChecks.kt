package app.lockedin.core

import app.lockedin.core.entitlement.EntitlementRules
import app.lockedin.core.entitlement.PlusEntitlement
import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiRules
import app.lockedin.core.intelligence.AiTask
import app.lockedin.core.session.FocusSession
import app.lockedin.core.session.Preset
import app.lockedin.core.session.SessionClock
import app.lockedin.core.sync.ConflictResolver
import app.lockedin.core.sync.Revision
import app.lockedin.core.sync.Winner

/**
 * Dependency-free checks for the shared core. Runs with `./gradlew :shared:verifyCore`
 * (also wired into `check`). Any failure throws and exits non-zero.
 */

private const val KAMPALA_OFFSET_MS = 3L * 60 * 60 * 1000
private const val MINUTE_MS = 60_000L

private var passed = 0
private var failed = 0

private fun check(name: String, condition: Boolean) {
    if (condition) {
        passed++
    } else {
        failed++
        println("FAIL: $name")
    }
}

private fun <T> checkEquals(name: String, expected: T, actual: T) {
    check("$name (expected=$expected, actual=$actual)", expected == actual)
}

fun main() {
    // --- Presets and Plus gating (must match Android Preset.paid) ---
    checkEquals("M2 is free", false, Preset.M2.requiresPlus)
    checkEquals("M25 is free (boundary)", false, Preset.M25.requiresPlus)
    checkEquals("M50 needs Plus", true, Preset.M50.requiresPlus)
    checkEquals("H3 needs Plus", true, Preset.H3.requiresPlus)
    checkEquals("MORNING needs Plus", true, Preset.MORNING.requiresPlus)
    checkEquals("unknown id falls back to M50", Preset.M50, Preset.fromId("nope"))
    checkEquals("id round trip", Preset.M90, Preset.fromId("90"))

    // --- Session end times ---
    val start = 1_795_000_000_000L
    checkEquals("25 minute end", start + 25 * MINUTE_MS, SessionClock.endAt(Preset.M25, start))
    checkEquals("3h end", start + 180 * MINUTE_MS, SessionClock.endAt(Preset.H3, start))

    // Kampala 20:00 on 19 Nov -> next 07:00 local on 20 Nov (= 04:00 UTC)
    checkEquals(
        "until-morning from Kampala evening",
        1_795_147_200_000L,
        SessionClock.endAt(Preset.MORNING, 1_795_107_600_000L, KAMPALA_OFFSET_MS),
    )
    // Kampala 01:00 on 20 Nov -> same day 07:00 local
    checkEquals(
        "until-morning before 07:00 stays the same day",
        1_795_147_200_000L,
        SessionClock.endAt(Preset.MORNING, 1_795_125_600_000L, KAMPALA_OFFSET_MS),
    )
    // Kampala 08:00 on 20 Nov -> rolls over to 07:00 on 21 Nov
    checkEquals(
        "until-morning after 07:00 rolls to next day",
        1_795_233_600_000L,
        SessionClock.endAt(Preset.MORNING, 1_795_150_800_000L, KAMPALA_OFFSET_MS),
    )
    // UTC device: 09:00 UTC on 19 Nov -> 07:00 UTC on 20 Nov
    checkEquals(
        "until-morning with zero offset",
        1_795_158_000_000L,
        SessionClock.endAt(Preset.MORNING, 1_795_078_800_000L, 0L),
    )

    // --- FocusSession ---
    val session = FocusSession.start("s1", Preset.M25, "  Read chapter 3  ", start)
    checkEquals("intention is trimmed", "Read chapter 3", session.intention)
    checkEquals("blank intention uses default", "Do it.", FocusSession.start("s2", Preset.M2, "   ", start).intention)
    checkEquals("intention capped at 80", 80, FocusSession.start("s3", Preset.M2, "x".repeat(200), start).intention.length)
    check("active before end", session.isActive(start + MINUTE_MS))
    check("inactive at end", !session.isActive(session.endsAtMs))
    checkEquals("remaining never negative", 0L, session.remainingMs(session.endsAtMs + 5_000))
    checkEquals("countdown format", "01:02:05", FocusSession.formatCountdown(3_725_000L))
    checkEquals("countdown format negative clamps", "00:00:00", FocusSession.formatCountdown(-1_000L))

    // --- Entitlements ---
    val now = 1_795_000_000_000L
    checkEquals("free preset starts without Plus", true, EntitlementRules.canStart(Preset.M25, null, now))
    checkEquals("paid preset blocked without Plus", false, EntitlementRules.canStart(Preset.M50, null, now))
    checkEquals(
        "paid preset allowed with active Plus",
        true,
        EntitlementRules.canStart(Preset.M50, PlusEntitlement(now + 1_000, now), now),
    )
    checkEquals(
        "paid preset blocked when Plus expired",
        false,
        EntitlementRules.canStart(Preset.M50, PlusEntitlement(now - 1, now - 1_000), now),
    )

    // --- Conflict resolution ---
    checkEquals(
        "newer timestamp wins (local)",
        Winner.LOCAL,
        ConflictResolver.resolve(Revision(200, "a"), Revision(100, "z")),
    )
    checkEquals(
        "newer timestamp wins (remote)",
        Winner.REMOTE,
        ConflictResolver.resolve(Revision(100, "a"), Revision(200, "z")),
    )
    checkEquals(
        "tie broken by device id, higher wins (local)",
        Winner.LOCAL,
        ConflictResolver.resolve(Revision(100, "b"), Revision(100, "a")),
    )
    checkEquals(
        "tie broken by device id, higher wins (remote)",
        Winner.REMOTE,
        ConflictResolver.resolve(Revision(100, "a"), Revision(100, "b")),
    )

    // --- AI request rules ---
    checkEquals("valid explain request", null, AiRules.validate(AiRequest(AiTask.EXPLAIN, "Photosynthesis notes")))
    checkEquals(
        "empty context rejected",
        "Select some study material first.",
        AiRules.validate(AiRequest(AiTask.SUMMARISE, "   ")),
    )
    checkEquals(
        "ASK needs a question",
        "Type a question first.",
        AiRules.validate(AiRequest(AiTask.ASK, "notes", question = " ")),
    )
    check(
        "oversized context rejected",
        AiRules.validate(AiRequest(AiTask.EXPLAIN, "x".repeat(AiRules.MAX_CONTEXT_CHARS + 1))) != null,
    )
    check(
        "oversized question rejected",
        AiRules.validate(AiRequest(AiTask.ASK, "notes", question = "q".repeat(AiRules.MAX_QUESTION_CHARS + 1))) != null,
    )

    println("shared core checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed shared core check(s) failed")
}
