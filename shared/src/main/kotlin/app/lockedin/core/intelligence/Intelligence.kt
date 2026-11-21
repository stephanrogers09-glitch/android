package app.lockedin.core.intelligence

/**
 * Contracts for LockedIn Intelligence (contextual study assistance inside a focus session).
 *
 * Platforms implement [AiProvider] by calling the server (Supabase Edge Function `ai-assist`),
 * which calls Gemini. No API key ever exists on a client. Keeping the provider behind this
 * interface lets the model or vendor change without touching the apps.
 */
enum class AiTask { EXPLAIN, SUMMARISE, REVISION_QUESTIONS, QUIZ, STUDY_PLAN, ASK }

data class AiRequest(
    val task: AiTask,
    /** Study material the user chose to share for this request. Only this text leaves the device. */
    val context: String,
    /** Required for [AiTask.ASK]; optional otherwise. */
    val question: String? = null,
    val sessionId: String? = null,
)

sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class Unavailable(val reason: Unavailability) : AiResult
    data class QuotaExceeded(val resetAtMs: Long?) : AiResult
    data class Rejected(val message: String) : AiResult
    data class Failed(val message: String) : AiResult
}

enum class Unavailability { NOT_SIGNED_IN, OFFLINE, NOT_CONFIGURED, SERVICE_DOWN }

interface AiProvider {
    suspend fun complete(request: AiRequest): AiResult
}

object AiRules {
    const val MAX_CONTEXT_CHARS = 12_000
    const val MAX_QUESTION_CHARS = 1_000

    /** Returns a user-facing reason when the request must not be sent, or null when it is valid. */
    fun validate(request: AiRequest): String? {
        if (request.context.length > MAX_CONTEXT_CHARS) {
            return "Share less material at once (limit $MAX_CONTEXT_CHARS characters)."
        }
        val question = request.question
        if (question != null && question.length > MAX_QUESTION_CHARS) {
            return "Keep the question under $MAX_QUESTION_CHARS characters."
        }
        if (request.task == AiTask.ASK && question.isNullOrBlank()) {
            return "Type a question first."
        }
        if (request.task != AiTask.ASK && request.context.isBlank()) {
            return "Select some study material first."
        }
        return null
    }
}
