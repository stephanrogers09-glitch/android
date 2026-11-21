package app.lockedin.core.auth

import java.util.Base64

/**
 * Checks the backend settings given at build time before anything is sent. Returns plain problems
 * the student or developer can act on, instead of letting a request fail with a crash or a vague error.
 */
object BackendSettings {
    const val URL_SETTING = "LOCKEDIN_SUPABASE_URL"
    const val KEY_SETTING = "LOCKEDIN_SUPABASE_ANON_KEY"

    /** Current Supabase keys. A publishable key is safe in a shipped app; a secret key never is. */
    const val PUBLISHABLE_PREFIX = "sb_publishable_"
    const val SECRET_PREFIX = "sb_secret_"

    fun problems(url: String, anonKey: String): List<String> {
        val out = mutableListOf<String>()
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            out += "$URL_SETTING is not set"
        } else if (!trimmedUrl.startsWith("https://") || trimmedUrl.length <= "https://".length) {
            out += "$URL_SETTING must be an https:// address"
        }
        val key = anonKey.trim()
        when {
            key.isEmpty() -> out += "$KEY_SETTING is not set"
            key.startsWith(SECRET_PREFIX) ->
                out += "$KEY_SETTING is a secret key. Use the publishable key; a secret key must never go in the app"
            key.startsWith(PUBLISHABLE_PREFIX) ->
                if (key.length == PUBLISHABLE_PREFIX.length) out += "$KEY_SETTING is incomplete"
            else -> when (val role = roleOf(key)) {
                "anon" -> Unit
                "service_role" ->
                    out += "$KEY_SETTING is a service role key. Use the publishable key; a service role key must never go in the app"
                null -> out += "$KEY_SETTING is not a Supabase publishable or anon key"
                else -> out += "$KEY_SETTING has role '$role', not the anon role"
            }
        }
        return out
    }

    /** The "role" claim of a Supabase JWT key, or null if the key is not a readable JWT. */
    fun roleOf(key: String): String? {
        val parts = key.split('.')
        if (parts.size != 3) return null
        return try {
            val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            Regex("\"role\"\\s*:\\s*\"([A-Za-z_]+)\"").find(payload)?.groupValues?.get(1)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
