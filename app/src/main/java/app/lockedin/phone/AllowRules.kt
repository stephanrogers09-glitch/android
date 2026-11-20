package app.lockedin.phone

/**
 * Pure allow/deny decision for the Focus Engine. Has no Android imports so it can be tested on a
 * JVM. [Allow] supplies the device-specific inputs.
 *
 * Replaces the old rule that allowed any package whose name contained "dialer", "incall",
 * "telecom" or "emergency". That rule let any app with those words in its name bypass the lock.
 */
object AllowRules {
    /** LockedIn itself and the system phone and telephony stack. */
    private val alwaysAllowed = setOf(
        "app.lockedin.phone",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
    )

    /**
     * Known call and emergency packages. The device's default dialer is also allowed (resolved at
     * runtime by [Allow]), so OEM dialers that are set as default do not need to be listed here.
     */
    private val knownCallPackages = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.incallui",
        "com.google.android.incallui",
        "com.android.emergency",
    )

    /** Photo apps stay blocked, even if the user chose to allow them. Same as before. */
    private val blockedNameParts = listOf("photo", "gallery", "album")

    fun isAllowed(packageName: String, defaultDialer: String?, permittedByUser: Boolean): Boolean {
        if (packageName in alwaysAllowed) return true
        val name = packageName.lowercase()
        if (blockedNameParts.any { name.contains(it) }) return false
        if (permittedByUser) return true
        if (defaultDialer != null && packageName == defaultDialer) return true
        return packageName in knownCallPackages
    }
}
