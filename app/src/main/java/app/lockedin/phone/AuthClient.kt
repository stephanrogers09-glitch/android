package app.lockedin.phone

import android.os.Handler
import android.os.Looper
import app.lockedin.core.auth.AuthFailure
import app.lockedin.core.auth.AuthResult
import app.lockedin.core.auth.AuthWire
import app.lockedin.core.auth.BackendSettings
import app.lockedin.core.auth.SessionManager
import app.lockedin.core.auth.SessionRefresher
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase Auth over HTTPS: sign-in, token refresh and revoke. Every call runs on a background thread.
 * Results reach the UI on the main thread. Nothing here stores a token; the SessionManager does that.
 */
class AuthClient(
    private val url: String = BackendConfig.supabaseUrl,
    private val anonKey: String = BackendConfig.anonKey,
) : SessionRefresher {
    private val main = Handler(Looper.getMainLooper())

    /** Signs in and stores the session. Calls back on the main thread with null on success, or a message. */
    fun signIn(manager: SessionManager, email: String, password: String, onDone: (String?) -> Unit) {
        BackendSettings.problems(url, anonKey).firstOrNull()?.let { onDone(it); return }
        if (email.isBlank() || password.isEmpty()) {
            onDone("Enter your email and password.")
            return
        }
        Thread {
            val message = try {
                val (status, body) = post(
                    AuthWire.PASSWORD_PATH,
                    AuthWire.encodeSignIn(email, password),
                    bearer = null,
                )
                when (val result = AuthWire.decodeSignIn(status, body, System.currentTimeMillis())) {
                    is AuthResult.Signed -> {
                        manager.begin(result.session)
                        null
                    }
                    is AuthResult.Failed -> messageFor(result.reason)
                }
            } catch (e: IOException) {
                "No connection. Try again when you are online."
            }
            main.post { onDone(message) }
        }.start()
    }

    /** Refreshes an access token. Called by the SessionManager on a background thread. */
    override fun refresh(refreshToken: String, nowMs: Long): AuthResult {
        if (BackendSettings.problems(url, anonKey).isNotEmpty()) {
            return AuthResult.Failed(AuthFailure.UNEXPECTED)
        }
        return try {
            val (status, body) = post(AuthWire.REFRESH_PATH, AuthWire.encodeRefresh(refreshToken), bearer = null)
            AuthWire.decodeRefresh(status, body, nowMs)
        } catch (e: IOException) {
            AuthResult.Failed(AuthFailure.UNEXPECTED)
        }
    }

    /** Best-effort: tells the server to revoke the session. Local sign-out never waits for this. */
    override fun revoke(accessToken: String) {
        if (BackendSettings.problems(url, anonKey).isNotEmpty()) return
        Thread {
            try {
                post(AuthWire.LOGOUT_PATH, "{}", bearer = accessToken)
            } catch (e: IOException) {
                // Offline: the local session is already gone. The server token expires on its own.
            }
        }.start()
    }

    private fun post(path: String, body: String, bearer: String?): Pair<Int, String> {
        val conn = URL(url + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("authorization", "Bearer ${bearer ?: anonKey}")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text: String = stream?.use { it.bufferedReader(Charsets.UTF_8).readText() }.orEmpty()
            return status to text
        } finally {
            conn.disconnect()
        }
    }

    private fun messageFor(reason: AuthFailure): String = when (reason) {
        AuthFailure.INVALID_CREDENTIALS -> "That email and password did not match an account."
        AuthFailure.SESSION_REJECTED -> "Your session has ended. Sign in again."
        AuthFailure.RATE_LIMITED -> "Too many attempts. Wait a few minutes and try again."
        AuthFailure.UNEXPECTED -> "Sign-in failed. Try again."
    }
}
