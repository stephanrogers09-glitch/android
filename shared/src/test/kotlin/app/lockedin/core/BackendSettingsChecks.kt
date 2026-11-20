package app.lockedin.core

import app.lockedin.core.auth.BackendSettings
import java.util.Base64

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

/** Builds an unsigned JWT-shaped key with the given role, as Supabase keys are. */
private fun key(role: String): String {
    val enc = Base64.getUrlEncoder().withoutPadding()
    val header = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".toByteArray())
    val payload = enc.encodeToString("{\"iss\":\"supabase\",\"role\":\"$role\"}".toByteArray())
    return "$header.$payload.signature"
}

fun main() {
    val url = "https://abc.supabase.co"
    check("valid settings give no problems", BackendSettings.problems(url, key("anon")).isEmpty())
    check("missing url is reported", BackendSettings.problems("", key("anon")).any { it.contains("LOCKEDIN_SUPABASE_URL is not set") })
    check("http url is refused", BackendSettings.problems("http://abc.supabase.co", key("anon")).any { it.contains("https") })
    check("bare https is refused", BackendSettings.problems("https://", key("anon")).any { it.contains("https") })
    check("missing key is reported", BackendSettings.problems(url, "").any { it.contains("LOCKEDIN_SUPABASE_ANON_KEY is not set") })
    check("service role key is refused",
        BackendSettings.problems(url, key("service_role")).any { it.contains("service role key") })
    check("non-JWT key is refused", BackendSettings.problems(url, "not-a-key").any { it.contains("is not a Supabase publishable or anon key") })
    check("role parsed from anon key", BackendSettings.roleOf(key("anon")) == "anon")
    check("role of garbage is null", BackendSettings.roleOf("a.b.c") == null)
    check("both missing gives both problems", BackendSettings.problems("", "").size == 2)
    check("publishable key accepted", BackendSettings.problems(url, "sb_publishable_abc123XYZ").isEmpty())
    check("publishable key with spaces accepted", BackendSettings.problems(url, "  sb_publishable_abc123  ").isEmpty())
    check("bare publishable prefix refused", BackendSettings.problems(url, "sb_publishable_").any { it.contains("incomplete") })
    check("secret key refused", BackendSettings.problems(url, "sb_secret_abc123").any { it.contains("secret key") })
    check("authenticated role refused", BackendSettings.problems(url, key("authenticated")).any { it.contains("role 'authenticated'") })
    check("legacy anon JWT still accepted", BackendSettings.problems(url, key("anon")).isEmpty())

    println("backend settings checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed backend settings check(s) failed")
}
