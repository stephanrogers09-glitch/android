package app.lockedin.core.study

import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiTask
import app.lockedin.core.intelligence.AiWire
import app.lockedin.core.intelligence.Json

/** The things a student can do with their material. Each one becomes one AI request. */
enum class StudyAction { EXPLAIN, SUMMARISE, QUIZ, CHECK, EXAMPLES }

object StudyPrompts {
    const val EXAMPLES_QUESTION =
        "Give two short worked examples that use this material, then one sentence on when each applies."

    /** Builds the request for an action. The material is always the text the student is studying. */
    fun requestFor(action: StudyAction, material: String): AiRequest = when (action) {
        StudyAction.EXPLAIN -> AiRequest(AiTask.EXPLAIN, material)
        StudyAction.SUMMARISE -> AiRequest(AiTask.SUMMARISE, material)
        StudyAction.QUIZ -> AiRequest(AiTask.QUIZ, material)
        StudyAction.CHECK -> AiRequest(AiTask.REVISION_QUESTIONS, material)
        StudyAction.EXAMPLES -> AiRequest(AiTask.ASK, material, EXAMPLES_QUESTION)
    }

    fun questionRequest(material: String, question: String): AiRequest =
        AiRequest(AiTask.ASK, material, question.trim())
}

object StudyMaterial {
    /** The selected text when the student has selected some, otherwise the whole note. */
    fun choose(note: String, selection: String): String = selection.trim().ifEmpty { note.trim() }
}

object StudyFocus {
    const val MAX_LINE_CHARS = 80

    /** The commitment line shown when a focus session starts from a note. */
    fun lineFor(noteTitle: String): String {
        val line = "Study: ${noteTitle.trim()}"
        return if (line.length <= MAX_LINE_CHARS) line else line.take(MAX_LINE_CHARS - 1).trimEnd() + "…"
    }
}

/**
 * What the study workspace remembers between visits: the note that was open, the question being
 * typed, and the last answer for that note. Stored only on this phone.
 */
data class StudyContext(
    val noteId: String? = null,
    val draftQuestion: String = "",
    val answerNoteId: String? = null,
    val answer: String = "",
) {
    /** The saved answer, only when it was produced for the note being shown. */
    fun answerFor(noteId: String): String = if (answerNoteId == noteId) answer else ""
}

object StudyContextCodec {
    private const val VERSION = 1
    const val MAX_DRAFT_CHARS = 1_000
    const val MAX_ANSWER_CHARS = 20_000

    fun encode(context: StudyContext): String =
        "{\"v\":$VERSION," +
            "\"noteId\":${nullableQuote(context.noteId)}," +
            "\"draft\":${AiWire.quote(context.draftQuestion.take(MAX_DRAFT_CHARS))}," +
            "\"answerNoteId\":${nullableQuote(context.answerNoteId)}," +
            "\"answer\":${AiWire.quote(context.answer.take(MAX_ANSWER_CHARS))}}"

    /** Anything unreadable becomes an empty context, so a damaged value never blocks the workspace. */
    fun decode(text: String?): StudyContext {
        if (text.isNullOrBlank()) return StudyContext()
        val root = Json.parseObjectOrNull(text) ?: return StudyContext()
        if (root["v"] != VERSION.toDouble()) return StudyContext()
        return StudyContext(
            noteId = root["noteId"] as? String,
            draftQuestion = (root["draft"] as? String).orEmpty().take(MAX_DRAFT_CHARS),
            answerNoteId = root["answerNoteId"] as? String,
            answer = (root["answer"] as? String).orEmpty().take(MAX_ANSWER_CHARS),
        )
    }

    private fun nullableQuote(value: String?): String = if (value == null) "null" else AiWire.quote(value)
}
