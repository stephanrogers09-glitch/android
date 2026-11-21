package app.lockedin.core.auth

/** Encrypted storage for the session record. The platform encrypts and decrypts; this sees text. */
interface SessionStore {
    /** The decrypted record, or null if none is stored or it cannot be read. */
    fun read(): String?
    fun write(record: String)
    fun delete()
}

/** Talks to the auth server. Called off the main thread. */
interface SessionRefresher {
    fun refresh(refreshToken: String, nowMs: Long): AuthResult
    /** Best-effort server-side revoke. Local sign-out must not depend on it. */
    fun revoke(accessToken: String)
}

/** What a caller gets when it asks for a token. */
sealed class TokenResult {
    data class Token(val value: String) : TokenResult()
    /** No session, or the session has ended. The student must sign in. */
    object SignedOut : TokenResult()
    /** A session exists but the server could not be reached to refresh it. Try again later. */
    object Unavailable : TokenResult()
}

/**
 * Owns the student's session lifecycle: restore after restart, refresh before expiry, end the session
 * when the refresh token is rejected, and sign out. Thread-safe; all state changes happen under one lock.
 */
class SessionManager(
    private val store: SessionStore,
    private val refresher: SessionRefresher,
) {
    private val lock = Any()
    private var loaded = false
    private var session: AuthSession? = null

    /** True when a session is stored (valid or expired). Reads local storage only; no network. */
    fun hasSession(): Boolean = synchronized(lock) { current() != null }

    /**
     * A token for the next request. Refreshes first when the access token is close to expiry.
     * May perform network I/O: call it off the main thread.
     */
    fun accessToken(nowMs: Long): TokenResult = synchronized(lock) { accessTokenLocked(nowMs) }

    private fun accessTokenLocked(nowMs: Long): TokenResult {
        val s = current() ?: return TokenResult.SignedOut
        if (s.isUsable(nowMs)) return TokenResult.Token(s.accessToken)
        return when (val result = refresher.refresh(s.refreshToken, nowMs)) {
            is AuthResult.Signed -> {
                save(result.session)
                TokenResult.Token(result.session.accessToken)
            }
            is AuthResult.Failed -> when (result.reason) {
                AuthFailure.SESSION_REJECTED -> {
                    clear()
                    TokenResult.SignedOut
                }
                else -> TokenResult.Unavailable
            }
        }
    }

    /** Signs out locally at once and returns the access token for a best-effort server revoke. */
    fun signOut(): String? = synchronized(lock) {
        val token = current()?.accessToken
        clear()
        token
    }

    /** Stores a session obtained from sign-in. */
    fun begin(newSession: AuthSession) = synchronized(lock) { save(newSession) }

    private fun current(): AuthSession? {
        if (!loaded) {
            loaded = true
            val raw = store.read()
            session = AuthWire.decodeRecord(raw)
            // A record that exists but cannot be decoded is discarded, so the student signs in cleanly.
            if (raw != null && session == null) store.delete()
        }
        return session
    }

    private fun save(newSession: AuthSession) {
        session = newSession
        loaded = true
        store.write(AuthWire.encodeRecord(newSession))
    }

    private fun clear() {
        session = null
        loaded = true
        store.delete()
    }
}
