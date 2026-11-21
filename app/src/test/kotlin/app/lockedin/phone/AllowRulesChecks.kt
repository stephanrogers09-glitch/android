package app.lockedin.phone

/**
 * JVM checks for the Focus Engine allowlist. Pure Kotlin, no Android needed.
 * Run with shared/scripts/verify-android-rules.sh. Any failure exits non-zero.
 */

private var passed = 0
private var failed = 0

private fun expect(name: String, expected: Boolean, actual: Boolean) {
    if (expected == actual) {
        passed++
    } else {
        failed++
        println("FAIL: $name (expected=$expected, actual=$actual)")
    }
}

fun main() {
    // The keyboard the student is typing with is allowed, so typing in a note does not bounce the lock.
    expect("default keyboard allowed", true, AllowRules.isAllowed("com.google.android.inputmethod.latin", null, false, defaultInputMethod = "com.google.android.inputmethod.latin"))
    expect("other keyboard not allowed", false, AllowRules.isAllowed("com.example.keyboard", null, false, defaultInputMethod = "com.google.android.inputmethod.latin"))
    expect("keyboard rule needs a default", false, AllowRules.isAllowed("com.google.android.inputmethod.latin", null, false))
    expect("social app still blocked with a keyboard set", false, AllowRules.isAllowed("com.example.social", null, false, defaultInputMethod = "com.google.android.inputmethod.latin"))

    // System phone stack and LockedIn itself are always allowed.
    expect("self allowed", true, AllowRules.isAllowed("app.lockedin.phone", null, false))
    expect("systemui allowed", true, AllowRules.isAllowed("com.android.systemui", null, false))
    expect("telephony allowed", true, AllowRules.isAllowed("com.android.phone", null, false))

    // Known call packages are allowed even when no default dialer is reported.
    expect("google dialer allowed", true, AllowRules.isAllowed("com.google.android.dialer", null, false))
    expect("aosp incallui allowed", true, AllowRules.isAllowed("com.android.incallui", null, false))

    // The device's default dialer is allowed by identity, not by name.
    expect(
        "default OEM dialer allowed by identity",
        true,
        AllowRules.isAllowed("com.oem.phonesuite", "com.oem.phonesuite", false),
    )

    // Regression: the old name-substring rule let these through. They must now be blocked.
    expect("fake dialer name blocked", false, AllowRules.isAllowed("com.evil.dialer.game", null, false))
    expect("fake incall name blocked", false, AllowRules.isAllowed("com.evil.incall.tool", null, false))
    expect("fake telecom name blocked", false, AllowRules.isAllowed("com.evil.telecom.x", null, false))
    expect("fake emergency name blocked", false, AllowRules.isAllowed("com.evil.emergency.kit", null, false))

    // A package is not allowed just because another app is the default dialer.
    expect("other app not allowed", false, AllowRules.isAllowed("com.example.game", "com.oem.phonesuite", false))

    // Photo apps stay blocked even when the user permitted them.
    expect("gallery blocked despite permit", false, AllowRules.isAllowed("com.example.gallery", null, true))
    expect("photos blocked despite permit", false, AllowRules.isAllowed("com.example.photos", null, true))

    // User-permitted apps are still allowed, as before.
    expect("user permitted allowed", true, AllowRules.isAllowed("com.example.reader", null, true))
    expect("unpermitted blocked", false, AllowRules.isAllowed("com.example.reader", null, false))

    // Regression: preinstalled OEM call screens that are not the default dialer stay allowed.
    expect(
        "system OEM incall allowed",
        true,
        AllowRules.isAllowed("com.oem.incallui", null, false, isSystemApp = true),
    )
    // The same name from a user-installed app is still blocked.
    expect(
        "third-party incall name blocked",
        false,
        AllowRules.isAllowed("com.oem.incallui", null, false, isSystemApp = false),
    )
    // System photo apps are still blocked.
    expect("system gallery blocked", false, AllowRules.isAllowed("com.oem.gallery", null, false, isSystemApp = true))

    println("allow rules checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed allowlist check(s) failed")
}
