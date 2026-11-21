package app.lockedin.core.study

/**
 * Practice from the student's own note, with no AI call: one key word in a sentence is blanked, and the
 * student types it. Deterministic, so the same note always gives the same questions.
 */
object ClozePractice {
    data class Question(val sentence: String, val prompt: String, val answer: String)

    private val stopWords = setOf(
        "about", "after", "again", "against", "because", "before", "being", "between", "could",
        "during", "other", "should", "their", "there", "these", "those", "through", "where", "which",
        "while", "without", "would",
    )
    private val sentenceEnd = Regex("(?<=[.!?])\\s+|\\n+")
    private val wordRun = Regex("[A-Za-z]{5,}")
    private val blank = "_____"

    /** Up to [limit] questions from the note's full sentences, in the order they are written. */
    fun questions(body: String, limit: Int = 8): List<Question> {
        val out = ArrayList<Question>()
        for (raw in body.split(sentenceEnd)) {
            if (out.size >= limit) break
            val sentence = raw.trim()
            if (sentence.isEmpty()) continue
            val words = sentence.split(Regex("\\s+")).size
            if (words !in 6..30) continue
            val target = wordRun.findAll(sentence).map { it.value }
                .filter { it.lowercase() !in stopWords }
                .maxByOrNull { it.length } ?: continue
            val match = Regex("\\b${Regex.escape(target)}\\b").find(sentence) ?: continue
            out.add(Question(sentence, sentence.replaceRange(match.range, blank), target))
        }
        return out
    }

    /** A typed answer is right if it matches ignoring case, spacing and final punctuation. */
    fun correct(given: String, answer: String): Boolean {
        val typed = normalise(given)
        return typed.isNotEmpty() && typed == normalise(answer)
    }

    private fun normalise(text: String): String =
        text.trim().trimEnd('.', ',', '!', '?', ';', ':').replace(Regex("\\s+"), " ").lowercase()
}
