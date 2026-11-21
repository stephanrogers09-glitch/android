package app.lockedin.core.study

/** Words that carry little meaning on their own. Shared by practice and key terms. */
object StudyWords {
    val stop: Set<String> = setOf(
        "about", "after", "again", "against", "because", "before", "being", "between", "could",
        "during", "other", "should", "their", "there", "these", "those", "through", "where", "which",
        "while", "without", "would",
    )
    val content = Regex("[A-Za-z]{5,}")
}

/** The most repeated meaningful words in a note. Counted, never ranked by an AI model. */
object KeyTerms {
    data class Term(val word: String, val count: Int)

    /** Words that appear at least twice, most repeated first. Ties sort alphabetically. */
    fun top(body: String, limit: Int = 5): List<Term> {
        val counts = HashMap<String, Int>()
        for (match in StudyWords.content.findAll(body)) {
            val word = match.value.lowercase()
            if (word in StudyWords.stop) continue
            counts[word] = (counts[word] ?: 0) + 1
        }
        return counts.entries
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { Term(it.key, it.value) }
    }
}
