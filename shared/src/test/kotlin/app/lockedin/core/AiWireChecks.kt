package app.lockedin.core

import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiResult
import app.lockedin.core.intelligence.AiTask
import app.lockedin.core.intelligence.AiWire
import app.lockedin.core.intelligence.Unavailability

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

fun main() {
    // Encoding escapes everything that would break the JSON body.
    val tricky = "He said \"hi\"\\path\nline\ttab\u0001"
    val encoded = AiWire.encodeRequest(AiRequest(AiTask.SUMMARISE, tricky))
    check("encode escapes quotes, backslash, newline, tab, control", encoded.contains("\\\"hi\\\"") &&
        encoded.contains("\\\\path") && encoded.contains("\\n") && encoded.contains("\\t") && encoded.contains("\\u0001"))
    check("encode omits question when null", !encoded.contains("question"))
    check("encode task name", encoded.startsWith("{\"task\":\"SUMMARISE\""))

    val asked = AiWire.encodeRequest(AiRequest(AiTask.ASK, "notes", "why?"))
    check("encode includes question when present", asked.contains("\"question\":\"why?\""))

    // Round trip: what the app encodes, the parser reads back unchanged.
    check("round trip keeps context exactly", encodedRoundTrip(tricky) == tricky)

    // Success and empty success.
    val ok = AiWire.decodeResponse(200, "{\"text\":\"  Plants make food.  \",\"remaining\":4,\"plus\":false}")
    check("200 returns trimmed text", ok is AiResult.Success && ok.text == "Plants make food.")
    check("200 empty text is Failed", AiWire.decodeResponse(200, "{\"text\":\"  \"}") is AiResult.Failed)
    check("200 unicode escape decodes", (AiWire.decodeResponse(200, "{\"text\":\"caf\\u00e9\"}") as? AiResult.Success)?.text == "café")

    // Status mapping.
    check("429 is QuotaExceeded", AiWire.decodeResponse(429, "{\"error\":\"quota_exceeded\"}") is AiResult.QuotaExceeded)
    check(
        "503 is service down",
        AiWire.decodeResponse(503, "{\"error\":\"ai_unavailable\"}") == AiResult.Unavailable(Unavailability.SERVICE_DOWN),
    )
    check(
        "401 is not signed in",
        AiWire.decodeResponse(401, "{\"error\":\"sign_in_required\"}") == AiResult.Unavailable(Unavailability.NOT_SIGNED_IN),
    )
    val rejected = AiWire.decodeResponse(400, "{\"error\":\"invalid_request\",\"message\":\"context is too long\"}")
    check("400 carries the server message", rejected == AiResult.Rejected("context is too long"))
    check("502 is Failed", AiWire.decodeResponse(502, "{\"error\":\"ai_failed\"}") is AiResult.Failed)

    // Malformed or empty bodies never throw.
    check("500 with HTML body is Failed", AiWire.decodeResponse(500, "<html>oops</html>") is AiResult.Failed)
    check("500 with empty body is Failed", AiWire.decodeResponse(500, "") is AiResult.Failed)
    check("200 with truncated JSON is Failed", AiWire.decodeResponse(200, "{\"text\":\"abc") is AiResult.Failed)

    // Parser edge cases.
    check("parser rejects trailing garbage", parseOrNull("{\"a\":1} x") == null)
    check("parser reads nested values", parseOrNull("{\"a\":[1,true,null,{\"b\":\"c\"}]}")?.get("a") is List<*>)

    println("ai wire checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed AI wire check(s) failed")
}

// Helpers that reach the internal JSON reader from the same module.
private fun encodedRoundTrip(value: String): String? {
    val body = AiWire.encodeRequest(AiRequest(AiTask.EXPLAIN, value))
    return parseOrNull(body)?.get("context") as? String
}

private fun parseOrNull(input: String): Map<String, Any?>? =
    app.lockedin.core.intelligence.Json.parseObjectOrNull(input)
