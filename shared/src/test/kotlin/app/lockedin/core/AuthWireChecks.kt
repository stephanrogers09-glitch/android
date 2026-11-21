package app.lockedin.core

import app.lockedin.core.auth.AuthFailure
import app.lockedin.core.auth.AuthResult
import app.lockedin.core.auth.AuthSession
import app.lockedin.core.auth.AuthWire

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

fun main() {
    // Request body: email is trimmed, password is sent exactly as typed, quotes are escaped.
    val body = AuthWire.encodeSignIn("  student@example.com ", "pa\"ss\\word")
    check("sign-in body trims email", body.contains("\"email\":\"student@example.com\""))
    check("sign-in body escapes password", body.contains("\"password\":\"pa\\\"ss\\\\word\""))

    val now = 1_000_000L
    val good = """{"access_token":"at-1","token_type":"bearer","expires_in":3600,"refresh_token":"rt-1","user":{"id":"u"}}"""
    val signed = AuthWire.decodeSignIn(200, good, now)
    check("200 with tokens signs in", signed is AuthResult.Signed)
    if (signed is AuthResult.Signed) {
        check("access token kept", signed.session.accessToken == "at-1")
        check("refresh token kept", signed.session.refreshToken == "rt-1")
        check("expiry = now + expires_in", signed.session.expiresAtMs == now + 3_600_000L)
        check("usable right after sign-in", signed.session.isUsable(now))
        check("not usable inside the margin", !signed.session.isUsable(now + 3_600_000L - 30_000L))
        check("not usable after expiry", !signed.session.isUsable(now + 3_600_001L))
        check("toString hides tokens", !signed.session.toString().contains("at-1") && !signed.session.toString().contains("rt-1"))
    }

    // Failures map to reasons that the UI can explain.
    check(
        "400 invalid_grant is invalid credentials",
        AuthWire.decodeSignIn(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}""", now) ==
            AuthResult.Failed(AuthFailure.INVALID_CREDENTIALS),
    )
    check("429 is rate limited", AuthWire.decodeSignIn(429, "{}", now) == AuthResult.Failed(AuthFailure.RATE_LIMITED))
    check("500 is unexpected", AuthWire.decodeSignIn(500, "<html>", now) == AuthResult.Failed(AuthFailure.UNEXPECTED))
    check(
        "200 without refresh token is unexpected",
        AuthWire.decodeSignIn(200, """{"access_token":"a","expires_in":60}""", now) ==
            AuthResult.Failed(AuthFailure.UNEXPECTED),
    )
    check(
        "200 with zero expiry is unexpected",
        AuthWire.decodeSignIn(200, """{"access_token":"a","refresh_token":"r","expires_in":0}""", now) ==
            AuthResult.Failed(AuthFailure.UNEXPECTED),
    )
    check("truncated 200 body is unexpected", AuthWire.decodeSignIn(200, "{\"access_token\":", now) ==
        AuthResult.Failed(AuthFailure.UNEXPECTED))

    // The session type is a plain value; equal inputs compare equal.
    check("session equality", AuthSession("a", "r", 5L) == AuthSession("a", "r", 5L))

    println("auth wire checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed auth check(s) failed")
}
