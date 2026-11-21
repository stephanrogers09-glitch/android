package app.lockedin.phone

import android.os.Handler
import android.os.Looper
import app.lockedin.core.auth.TokenResult
import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiResult
import app.lockedin.core.intelligence.AiRules
import app.lockedin.core.intelligence.AiWire
import app.lockedin.core.intelligence.Unavailability
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends one AI request to the `ai-assist` function. The device never holds a Gemini key; the function
 * holds it and calls Gemini. The access token is fetched on the background thread, because a refresh
 * can need the network.
 */
class AiClient(
    private val functionUrl: () -> String = { BackendConfig.aiFunctionUrl() },
    private val setupProblem: () -> String? = { BackendConfig.problem() },
    private val token: () -> TokenResult,
) {
    private val main = Handler(Looper.getMainLooper())

    /** Calls back on the main thread. Validation and setup problems are reported without any network call. */
    fun send(request: AiRequest, onResult: (AiResult) -> Unit) {
        AiRules.validate(request)?.let { onResult(AiResult.Rejected(it)); return }
        if (setupProblem() != null) {
            onResult(AiResult.Unavailable(Unavailability.NOT_CONFIGURED))
            return
        }
        Thread {
            val result = try {
                when (val t = token()) {
                    is TokenResult.Token -> post(request, t.value)
                    is TokenResult.SignedOut -> AiResult.Unavailable(Unavailability.NOT_SIGNED_IN)
                    is TokenResult.Unavailable -> AiResult.Unavailable(Unavailability.OFFLINE)
                }
            } catch (e: IOException) {
                AiResult.Unavailable(Unavailability.OFFLINE)
            }
            main.post { onResult(result) }
        }.start()
    }

    private fun post(request: AiRequest, accessToken: String): AiResult {
        val conn = URL(functionUrl()).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("apikey", BackendConfig.anonKey)
            conn.setRequestProperty("authorization", "Bearer $accessToken")
            conn.outputStream.use { it.write(AiWire.encodeRequest(request).toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body: String = stream?.use { it.bufferedReader(Charsets.UTF_8).readText() }.orEmpty()
            return AiWire.decodeResponse(status, body)
        } finally {
            conn.disconnect()
        }
    }
}
