package app.lockedin.core.intelligence

/**
 * Wire format between apps and the `ai-assist` Edge Function. Pure Kotlin so that every platform
 * shares the same encoding and error mapping, and so it can be checked on the JVM without Android.
 *
 * Request body:  {"task":"EXPLAIN","context":"...","question":"..."}
 * Success body:  {"text":"...","remaining":4,"plus":false}
 * Error bodies:  {"error":"quota_exceeded","reason":"user_limit"}, {"error":"ai_failed"}, ...
 */
object AiWire {

    fun encodeRequest(request: AiRequest): String {
        val parts = mutableListOf(
            "\"task\":" + quote(request.task.name),
            "\"context\":" + quote(request.context),
        )
        request.question?.let { parts += "\"question\":" + quote(it) }
        return "{" + parts.joinToString(",") + "}"
    }

    /** Maps an HTTP status and body from the function to an [AiResult]. Never throws. */
    fun decodeResponse(status: Int, body: String): AiResult {
        val fields = Json.parseObjectOrNull(body) ?: emptyMap()
        val error = fields["error"] as? String
        return when (status) {
            200 -> {
                val text = (fields["text"] as? String)?.trim().orEmpty()
                if (text.isEmpty()) AiResult.Failed("The assistant returned nothing. Try again.")
                else AiResult.Success(text)
            }
            400 -> AiResult.Rejected(
                (fields["message"] as? String) ?: "That request could not be sent.",
            )
            401 -> AiResult.Unavailable(Unavailability.NOT_SIGNED_IN)
            429 -> AiResult.QuotaExceeded(resetAtMs = null)
            503 -> AiResult.Unavailable(Unavailability.SERVICE_DOWN)
            502 -> AiResult.Failed("The assistant could not answer. Your request was not charged.")
            else -> AiResult.Failed(
                if (error.isNullOrBlank()) "Unexpected response ($status)." else "Unexpected response: $error",
            )
        }
    }

    internal fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}

/**
 * Minimal JSON reader for the function's responses. Handles objects, arrays, strings (with
 * escapes), numbers, booleans and null. Returns null for anything malformed instead of throwing.
 */
internal object Json {
    fun parseObjectOrNull(input: String): Map<String, Any?>? {
        return try {
            val p = Parser(input)
            p.skip()
            val value = p.value()
            p.skip()
            if (p.atEnd() && value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                value as Map<String, Any?>
            } else null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i == s.length

        fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skip()
            require(i < s.length) { "unexpected end" }
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skip()
            if (peek() == '}') { i++; return out }
            while (true) {
                skip()
                val key = str()
                skip()
                require(peek() == ':') { "expected ':'" }
                i++
                out[key] = value()
                skip()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> throw IllegalArgumentException("expected ',' or '}'")
                }
            }
        }

        private fun arr(): List<Any?> {
            i++
            val out = mutableListOf<Any?>()
            skip()
            if (peek() == ']') { i++; return out }
            while (true) {
                out += value()
                skip()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> throw IllegalArgumentException("expected ',' or ']'")
                }
            }
        }

        private fun str(): String {
            require(peek() == '"') { "expected string" }
            i++
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "unterminated string" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < s.length) { "bad escape" }
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                require(i + 4 <= s.length) { "bad unicode escape" }
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            require(i > start) { "expected a value" }
            return s.substring(start, i).toDouble()
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "expected $word" }
            i += word.length
            return v
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
    }
}
