package app.lockedin.core

import app.lockedin.core.study.ClozePractice

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

    println("practice checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed practice check(s) failed")
}
