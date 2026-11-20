package app.lockedin.core.entitlement

import app.lockedin.core.session.Preset

/**
 * Plus access as last confirmed by the server.
 * [untilMs] is the expiry; [verifiedAtMs] is when the server last confirmed it.
 * The server is the authority. Clients cache this value so the Focus Engine still works offline
 * until the cached expiry passes.
 */
data class PlusEntitlement(val untilMs: Long, val verifiedAtMs: Long) {
    fun isActive(nowMs: Long): Boolean = untilMs > nowMs
}

object EntitlementRules {
    /** A preset can start only if it is free or Plus is active at [nowMs]. */
    fun canStart(preset: Preset, plus: PlusEntitlement?, nowMs: Long): Boolean =
        !preset.requiresPlus || (plus?.isActive(nowMs) == true)
}
