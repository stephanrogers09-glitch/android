package app.lockedin.core.auth

import app.lockedin.core.intelligence.Json

/**
 * Sign-in for the study session, using Supabase Auth's password grant:
 *   POST {supabaseUrl}/auth/v1/token?grant_type=password   body: {"email":"...","password":"..."}
 * Pure Kotlin. The platform performs the HTTP call and passes the status and body here.
 */
object AuthWire {
    const val TOKEN_PATH = "/auth/v1/token?grant_type=password"

    /** Refresh a few minutes early so a request does not start with an expired token. */
    const val EXPIRY_MARGIN_MS = 60_000L

    fun encodeSignIn(email: String, password: String): String =
        "{\"email\":" + quote(email.trim()) + ",\"password\":" + quote(password) + "}"

    /**
     * Maps Supabase's response to an [AuthResult]. [nowMs] is the device time when the response
     * arrived, used to compute the token expiry. Never includes the password or tokens in errors.
     */
    fun decodeSignIn(status: Int, body: String, nowMs: Long): AuthResult {
        if (status == 200) {
            val fields = Json.parseObjectOrNull(body) ?: return AuthResult.Failed(AuthFailure.UNEXPECTED)
            val access = fields["access_token"] as? String
            val refresh = fields["refresh_token"] as? String
            val expiresIn = (fields["expires_in"] as? Number)?.toLong()
            if (access.isNullOrBlank() || refresh.isNullOrBlank() || expiresIn == null || expiresIn <= 0) {
                return AuthResult.Failed(AuthFailure.UNEXPECTED)
            }
            return AuthResult.Signed(
                AuthSession(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAtMs = nowMs + expiresIn * 1000L,
                ),
            )
        }
        return when (status) {
            400, 401 -> AuthResult.Failed(AuthFailure.INVALID_CREDENTIALS)
            429 -> AuthResult.Failed(AuthFailure.RATE_LIMITED)
            else -> AuthResult.Failed(AuthFailure.UNEXPECTED)
        }
    }

    internal fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2).append('"')
        for (c in value) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
) {
    /** True while the access token is valid with the safety margin. */
    fun isUsable(nowMs: Long): Boolean = nowMs < expiresAtMs - AuthWire.EXPIRY_MARGIN_MS

    // Tokens must not appear in logs or string dumps.
    override fun toString(): String = "AuthSession(expiresAtMs=$expiresAtMs)"
}

sealed class AuthResult {
    data class Signed(val session: AuthSession) : AuthResult()
    data class Failed(val reason: AuthFailure) : AuthResult()
}

enum class AuthFailure { INVALID_CREDENTIALS, RATE_LIMITED, UNEXPECTED }
