package app.lockedin.core

import app.lockedin.core.study.ClozePractice
import app.lockedin.core.study.CommitmentSuggestions
import app.lockedin.core.study.KeyTerms

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

fun main() {
    val note = "Photosynthesis converts light energy into chemical energy stored in glucose. " +
        "Short line. Chlorophyll absorbs mostly red and blue light during the day."

    val questions = ClozePractice.questions(note)
    check("two full sentences give two questions", questions.size == 2)
    val first = questions.first()
    check("the longest content word is the answer", first.answer == "Photosynthesis")
    check("the answer is blanked from the prompt", first.prompt == "_____ converts light energy into chemical energy stored in glucose.")
    check("the sentence is kept whole", first.sentence.startsWith("Photosynthesis converts"))
    check("short fragments are skipped", questions.none { it.sentence == "Short line." })

    check("no questions from an empty note", ClozePractice.questions("").isEmpty())
    check("no questions from fragments only", ClozePractice.questions("Too short. Also short.").isEmpty())
    check("the limit is respected", ClozePractice.questions(note.repeat(10), limit = 3).size == 3)
    check("the same note gives the same questions", ClozePractice.questions(note) == ClozePractice.questions(note))

    check("exact answer is right", ClozePractice.correct("Photosynthesis", "Photosynthesis"))
    check("case and spacing do not matter", ClozePractice.correct("  photosynthesis ", "Photosynthesis"))
    check("final punctuation does not matter", ClozePractice.correct("photosynthesis.", "Photosynthesis"))
    check("a wrong word is wrong", !ClozePractice.correct("respiration", "Photosynthesis"))
    check("a blank answer is never right", !ClozePractice.correct("   ", "Photosynthesis"))

    val phrases = CommitmentSuggestions.all
    check("there are several suggestions", phrases.size >= 8)
    check("suggestions are unique", phrases.toSet().size == phrases.size)
    check("suggestions are short enough to show", phrases.all { it.length in 8..80 })
    check("rotation wraps to the start", CommitmentSuggestions.at(phrases.size) == phrases.first())
    check("rotation handles negative steps", CommitmentSuggestions.at(-1) == phrases.last())
    check("rotation moves to a different phrase", CommitmentSuggestions.at(0) != CommitmentSuggestions.at(1))

    val terms = KeyTerms.top("Neurons carry signals. Neurons fire. Signals travel fast. Synapses matter.")
    check("repeated meaningful words are key terms", terms.map { it.word } == listOf("neurons", "signals"))
    check("a word seen once is not a key term", terms.none { it.word == "synapses" })
    check("no key terms from an empty note", KeyTerms.top("").isEmpty())
    check("key terms respect the limit", KeyTerms.top("alpha alpha bravo bravo charlie charlie", limit = 2).size == 2)

    println("practice checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed practice check(s) failed")
}
