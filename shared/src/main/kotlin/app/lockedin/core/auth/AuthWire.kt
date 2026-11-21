package app.lockedin.core.auth

import app.lockedin.core.intelligence.Json

/**
 * Supabase Auth wire format, pure Kotlin. The platform performs the HTTP calls and passes status and
 * body here; this file never touches the network or storage.
 *
 *   sign in   POST {url}/auth/v1/token?grant_type=password       {"email":..,"password":..}
 *   refresh   POST {url}/auth/v1/token?grant_type=refresh_token  {"refresh_token":..}
 *   sign out  POST {url}/auth/v1/logout   (bearer = access token; revokes the refresh token)
 */
object AuthWire {
    const val PASSWORD_PATH = "/auth/v1/token?grant_type=password"
    const val REFRESH_PATH = "/auth/v1/token?grant_type=refresh_token"
    const val LOGOUT_PATH = "/auth/v1/logout"

    /** Refresh a few minutes early so a request does not start with an expired token. */
    const val EXPIRY_MARGIN_MS = 60_000L

    /** Version of the stored record. Bump it and the reader discards older records. */
    const val RECORD_VERSION = 1

    fun encodeSignIn(email: String, password: String): String =
        "{\"email\":" + quote(email.trim()) + ",\"password\":" + quote(password) + "}"

    fun encodeRefresh(refreshToken: String): String =
        "{\"refresh_token\":" + quote(refreshToken) + "}"

    /** Sign-in response mapping. [nowMs] is the device time when the response arrived. */
    fun decodeSignIn(status: Int, body: String, nowMs: Long): AuthResult {
        if (status == 200) return parseSession(body, nowMs)
        return when (status) {
            400, 401 -> AuthResult.Failed(AuthFailure.INVALID_CREDENTIALS)
            429 -> AuthResult.Failed(AuthFailure.RATE_LIMITED)
            else -> AuthResult.Failed(AuthFailure.UNEXPECTED)
        }
    }

    /**
     * Refresh response mapping. A 400 or 401 means the refresh token itself is no longer valid, so
     * the session has ended. Anything else (server or network trouble) is retryable, not a sign-out.
     */
    fun decodeRefresh(status: Int, body: String, nowMs: Long): AuthResult {
        if (status == 200) return parseSession(body, nowMs)
        return when (status) {
            400, 401 -> AuthResult.Failed(AuthFailure.SESSION_REJECTED)
            429 -> AuthResult.Failed(AuthFailure.RATE_LIMITED)
            else -> AuthResult.Failed(AuthFailure.UNEXPECTED)
        }
    }

    private fun parseSession(body: String, nowMs: Long): AuthResult {
        val fields = Json.parseObjectOrNull(body) ?: return AuthResult.Failed(AuthFailure.UNEXPECTED)
        val access = fields["access_token"] as? String
        val refresh = fields["refresh_token"] as? String
        val expiresIn = (fields["expires_in"] as? Number)?.toLong()
        if (access.isNullOrBlank() || refresh.isNullOrBlank() || expiresIn == null || expiresIn <= 0) {
            return AuthResult.Failed(AuthFailure.UNEXPECTED)
        }
        return AuthResult.Signed(
            AuthSession(accessToken = access, refreshToken = refresh, expiresAtMs = nowMs + expiresIn * 1000L),
        )
    }

    /**
     * Serialises a session for encrypted storage. The caller encrypts the result. The format is a
     * versioned JSON object so a future change can be detected and discarded.
     */
    fun encodeRecord(session: AuthSession): String =
        "{\"v\":$RECORD_VERSION,\"access\":" + quote(session.accessToken) +
            ",\"refresh\":" + quote(session.refreshToken) +
            ",\"expiresAtMs\":" + session.expiresAtMs + "}"

    /** Returns the session, or null if the record is missing, corrupt or from another version. */
    fun decodeRecord(record: String?): AuthSession? {
        if (record.isNullOrBlank()) return null
        val fields = Json.parseObjectOrNull(record) ?: return null
        if ((fields["v"] as? Number)?.toInt() != RECORD_VERSION) return null
        val access = fields["access"] as? String ?: return null
        val refresh = fields["refresh"] as? String ?: return null
        val expires = (fields["expiresAtMs"] as? Number)?.toLong() ?: return null
        if (access.isBlank() || refresh.isBlank()) return null
        return AuthSession(access, refresh, expires)
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

enum class AuthFailure {
    /** Email or password was wrong. */
    INVALID_CREDENTIALS,
    /** The refresh token was rejected, so the session has ended and the student must sign in again. */
    SESSION_REJECTED,
    RATE_LIMITED,
    /** Server or network trouble. The stored session is kept so the app can retry. */
    UNEXPECTED,
}
