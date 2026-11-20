package app.lockedin.core

import app.lockedin.core.auth.AuthFailure
import app.lockedin.core.auth.AuthResult
import app.lockedin.core.auth.AuthSession
import app.lockedin.core.auth.AuthWire
import app.lockedin.core.auth.SessionManager
import app.lockedin.core.auth.SessionRefresher
import app.lockedin.core.auth.SessionStore
import app.lockedin.core.auth.TokenResult

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

private class MemoryStore : SessionStore {
    var record: String? = null
    var writes = 0
    override fun read(): String? = record
    override fun write(record: String) { this.record = record; writes++ }
    override fun delete() { record = null }
}

/** Returns queued results in order and records every refresh it was asked to perform. */
private class ScriptedRefresher(private vararg val results: AuthResult) : SessionRefresher {
    val refreshTokens = mutableListOf<String>()
    var revoked = 0
    private var next = 0
    override fun refresh(refreshToken: String, nowMs: Long): AuthResult {
        refreshTokens += refreshToken
        return results[next++]
    }
    override fun revoke(accessToken: String) { revoked++ }
}

private fun session(access: String, refresh: String, expiresAt: Long) = AuthSession(access, refresh, expiresAt)

private const val NOW = 1_000_000L
private const val LONG_LIFE = NOW + 3_600_000L

fun main() {
    // --- Lifecycle -----------------------------------------------------------------------------
    run {
        val store = MemoryStore()
        val manager = SessionManager(store, ScriptedRefresher())
        check("no session: signed out", manager.accessToken(NOW) == TokenResult.SignedOut)
        check("no session: hasSession false", !manager.hasSession())
    }

    run {
        val store = MemoryStore()
        val refresher = ScriptedRefresher()
        val manager = SessionManager(store, refresher)
        manager.begin(session("at-1", "rt-1", LONG_LIFE))
        check("usable token is returned", manager.accessToken(NOW) == TokenResult.Token("at-1"))
        check("usable token does not refresh", refresher.refreshTokens.isEmpty())
    }

    run {
        // Restart: a new manager over the same store restores the session.
        val store = MemoryStore()
        SessionManager(store, ScriptedRefresher()).begin(session("at-1", "rt-1", LONG_LIFE))
        val restarted = SessionManager(store, ScriptedRefresher())
        check("restart: session restored", restarted.hasSession())
        check("restart: token works", restarted.accessToken(NOW) == TokenResult.Token("at-1"))
    }

    run {
        // Expired access token refreshes, and the refresh token rotates into storage.
        val store = MemoryStore()
        val refresher = ScriptedRefresher(
            AuthResult.Signed(session("at-2", "rt-2", LONG_LIFE)),
        )
        val manager = SessionManager(store, refresher)
        manager.begin(session("at-1", "rt-1", NOW - 1))
        check("refresh: new token returned", manager.accessToken(NOW) == TokenResult.Token("at-2"))
        check("refresh: used the old refresh token", refresher.refreshTokens == listOf("rt-1"))
        check("refresh: rotated token persisted", AuthWire.decodeRecord(store.record)?.refreshToken == "rt-2")
        check("refresh: next call needs no network", manager.accessToken(NOW) == TokenResult.Token("at-2") &&
            refresher.refreshTokens.size == 1)
    }

    run {
        // Refresh token rejected: the session ends and nothing is left in storage.
        val store = MemoryStore()
        val manager = SessionManager(store, ScriptedRefresher(AuthResult.Failed(AuthFailure.SESSION_REJECTED)))
        manager.begin(session("at-1", "rt-1", NOW - 1))
        check("rejected refresh: signed out", manager.accessToken(NOW) == TokenResult.SignedOut)
        check("rejected refresh: storage cleared", store.record == null)
        check("rejected refresh: hasSession false", !manager.hasSession())
    }

    run {
        // Network trouble: the session is kept and the next call retries the refresh.
        val store = MemoryStore()
        val refresher = ScriptedRefresher(
            AuthResult.Failed(AuthFailure.UNEXPECTED),
            AuthResult.Signed(session("at-2", "rt-2", LONG_LIFE)),
        )
        val manager = SessionManager(store, refresher)
        manager.begin(session("at-1", "rt-1", NOW - 1))
        check("offline refresh: unavailable, not signed out", manager.accessToken(NOW) == TokenResult.Unavailable)
        check("offline refresh: session kept", manager.hasSession() && store.record != null)
        check("retry after offline succeeds", manager.accessToken(NOW) == TokenResult.Token("at-2"))
        check("retry used the same refresh token", refresher.refreshTokens == listOf("rt-1", "rt-1"))
    }

    run {
        // Rate limiting is also retryable, not a sign-out.
        val manager = SessionManager(
            MemoryStore(),
            ScriptedRefresher(AuthResult.Failed(AuthFailure.RATE_LIMITED)),
        )
        manager.begin(session("at-1", "rt-1", NOW - 1))
        check("rate limited: unavailable", manager.accessToken(NOW) == TokenResult.Unavailable)
    }

    run {
        // Sign-out clears local state at once and returns the token for a server revoke.
        val store = MemoryStore()
        val refresher = ScriptedRefresher()
        val manager = SessionManager(store, refresher)
        manager.begin(session("at-1", "rt-1", LONG_LIFE))
        check("sign-out returns access token", manager.signOut() == "at-1")
        check("sign-out clears storage", store.record == null)
        check("after sign-out: signed out", manager.accessToken(NOW) == TokenResult.SignedOut)
        check("sign-out does not refresh", refresher.refreshTokens.isEmpty())
        check("sign-out without session returns null", manager.signOut() == null)
    }

    run {
        // A corrupt record is discarded, so the student signs in cleanly instead of crashing.
        val store = MemoryStore()
        store.record = "{not json"
        val manager = SessionManager(store, ScriptedRefresher())
        check("corrupt record: signed out", !manager.hasSession())
        check("corrupt record: deleted", store.record == null)
    }

    // --- Record format -----------------------------------------------------------------------
    val original = session("a\"t", "r\\t", 42L)
    val decoded = AuthWire.decodeRecord(AuthWire.encodeRecord(original))
    check("record round trip", decoded == original)
    check("record from another version is discarded",
        AuthWire.decodeRecord("{\"v\":99,\"access\":\"a\",\"refresh\":\"r\",\"expiresAtMs\":1}") == null)
    check("record missing refresh is discarded",
        AuthWire.decodeRecord("{\"v\":1,\"access\":\"a\",\"expiresAtMs\":1}") == null)
    check("null record is no session", AuthWire.decodeRecord(null) == null)

    // --- Refresh response mapping ----------------------------------------------------------
    val fresh = AuthWire.decodeRefresh(200, """{"access_token":"a","refresh_token":"r","expires_in":60}""", NOW)
    check("refresh 200 signs in", fresh is AuthResult.Signed)
    check("refresh 400 ends session", AuthWire.decodeRefresh(400, "{}", NOW) == AuthResult.Failed(AuthFailure.SESSION_REJECTED))
    check("refresh 401 ends session", AuthWire.decodeRefresh(401, "{}", NOW) == AuthResult.Failed(AuthFailure.SESSION_REJECTED))
    check("refresh 500 is retryable", AuthWire.decodeRefresh(500, "", NOW) == AuthResult.Failed(AuthFailure.UNEXPECTED))
    check("refresh 429 is rate limited", AuthWire.decodeRefresh(429, "", NOW) == AuthResult.Failed(AuthFailure.RATE_LIMITED))
    check("refresh body is valid JSON with token",
        AuthWire.encodeRefresh("rt\"x") == "{\"refresh_token\":\"rt\\\"x\"}")

    run {
        // Concurrent callers on an expired session must share one refresh. A second refresh with the
        // already-rotated token would be rejected and end the session.
        val store = MemoryStore()
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val refresher = object : SessionRefresher {
            override fun refresh(refreshToken: String, nowMs: Long): AuthResult {
                calls.incrementAndGet()
                Thread.sleep(50)
                return AuthResult.Signed(session("at-new", "rt-new", LONG_LIFE))
            }
            override fun revoke(accessToken: String) {}
        }
        val manager = SessionManager(store, refresher)
        manager.begin(session("at-old", "rt-old", NOW - 1))
        val results = java.util.Collections.synchronizedList(mutableListOf<TokenResult>())
        val threads = (1..8).map { Thread { results.add(manager.accessToken(NOW)) } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        check("concurrent callers refresh once", calls.get() == 1)
        check("every caller gets the new token",
            results.size == 8 && results.all { it == TokenResult.Token("at-new") })
        check("the stored refresh token is the rotated one", AuthWire.decodeRecord(store.record)?.refreshToken == "rt-new")
    }

    println("session lifecycle checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed session check(s) failed")
}
