package app.lockedin.phone

import android.os.Handler
import android.os.Looper
import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiResult
import app.lockedin.core.intelligence.AiRules
import app.lockedin.core.intelligence.AiWire
import app.lockedin.core.intelligence.Unavailability
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends one AI request to the `ai-assist` Edge Function. The device never holds a Gemini key.
 *
 * [functionUrl] stays blank until the Supabase project exists, and [accessToken] returns null
 * until device sign-in exists. Until then every request returns a clear state, not a fake answer.
 * Callbacks run on the main thread.
 */
class AiClient(
    private val functionUrl: String = FUNCTION_URL,
    private val accessToken: () -> String? = { null },
) {
    private val main = Handler(Looper.getMainLooper())

    fun send(request: AiRequest, onResult: (AiResult) -> Unit) {
        AiRules.validate(request)?.let { onResult(AiResult.Rejected(it)); return }
        if (functionUrl.isBlank()) {
            onResult(AiResult.Unavailable(Unavailability.NOT_CONFIGURED))
            return
        }
        val token = accessToken()
        if (token.isNullOrBlank()) {
            onResult(AiResult.Unavailable(Unavailability.NOT_SIGNED_IN))
            return
        }
        Thread {
            val result = try {
                post(request, token)
            } catch (e: IOException) {
                AiResult.Unavailable(Unavailability.OFFLINE)
            }
            main.post { onResult(result) }
        }.start()
    }

    private fun post(request: AiRequest, token: String): AiResult {
        val conn = URL(functionUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("authorization", "Bearer $token")
            conn.outputStream.use { it.write(AiWire.encodeRequest(request).toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body: String = stream?.use { it.bufferedReader(Charsets.UTF_8).readText() }.orEmpty()
            return AiWire.decodeResponse(status, body)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Set to https://<project>.supabase.co/functions/v1/ai-assist once the project exists. */
        const val FUNCTION_URL = ""
    }
}
