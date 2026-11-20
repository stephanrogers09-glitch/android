package app.lockedin.phone

import android.os.Handler
import android.os.Looper
import app.lockedin.core.auth.AuthFailure
import app.lockedin.core.auth.AuthResult
import app.lockedin.core.auth.AuthSession
import app.lockedin.core.auth.AuthWire
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Signs the student in to Supabase Auth so the AI helper can send their access token.
 * Both constants stay blank until the Supabase project exists; until then sign-in reports
 * "not connected" and nothing is sent anywhere.
 */
object AuthClient {
    /** https://<project>.supabase.co once the project exists. Public by design. */
    const val SUPABASE_URL = ""
    /** The project's anon (publishable) key. Never the service role key. */
    const val SUPABASE_ANON_KEY = ""

    private val main = Handler(Looper.getMainLooper())

    /** Calls back on the main thread with null on success, or a message to show the student. */
    fun signIn(email: String, password: String, onDone: (String?) -> Unit) {
        if (SUPABASE_URL.isBlank() || SUPABASE_ANON_KEY.isBlank()) {
            onDone("Sign-in is not connected to a server yet.")
            return
        }
        if (email.isBlank() || password.isEmpty()) {
            onDone("Enter your email and password.")
            return
        }
        Thread {
            val message = try {
                val (status, body) = post(email, password)
                when (val result = AuthWire.decodeSignIn(status, body, System.currentTimeMillis())) {
                    is AuthResult.Signed -> {
                        AuthStore.set(result.session)
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

    fun signOut() = AuthStore.clear()

    private fun post(email: String, password: String): Pair<Int, String> {
        val conn = URL(SUPABASE_URL.trimEnd('/') + AuthWire.TOKEN_PATH).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            conn.outputStream.use { it.write(AuthWire.encodeSignIn(email, password).toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body: String = stream?.use { it.bufferedReader(Charsets.UTF_8).readText() }.orEmpty()
            return status to body
        } finally {
            conn.disconnect()
        }
    }

    private fun messageFor(reason: AuthFailure): String = when (reason) {
        AuthFailure.INVALID_CREDENTIALS -> "That email and password did not match an account."
        AuthFailure.RATE_LIMITED -> "Too many attempts. Wait a few minutes and try again."
        AuthFailure.UNEXPECTED -> "Sign-in failed. Try again."
    }
}

/**
 * Holds the signed-in session for this app run only. Tokens are not written to disk: plain
 * SharedPreferences is not safe for them. Persisting them needs Android Keystore-backed storage,
 * which is a follow-up.
 */
object AuthStore {
    @Volatile
    private var session: AuthSession? = null

    fun set(value: AuthSession) {
        session = value
    }

    fun clear() {
        session = null
    }

    fun isSignedIn(): Boolean = session != null

    /** The access token when it is still valid, otherwise null (the student signs in again). */
    fun usableAccessToken(nowMs: Long = System.currentTimeMillis()): String? =
        session?.takeIf { it.isUsable(nowMs) }?.accessToken
}
