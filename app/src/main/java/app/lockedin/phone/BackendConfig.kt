package app.lockedin.phone

import app.lockedin.core.auth.BackendSettings

/** Backend settings from the build. Empty values are reported by [problem], never used. */
object BackendConfig {
    val supabaseUrl: String get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    /** A message for the student or developer when the study session cannot reach the server, else null. */
    fun problem(): String? = BackendSettings.problems(supabaseUrl, anonKey).firstOrNull()?.let {
        "Study session is not set up in this build: $it."
    }

    fun aiFunctionUrl(): String = "$supabaseUrl/functions/v1/ai-assist"
}
