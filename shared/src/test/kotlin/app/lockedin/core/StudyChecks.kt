package app.lockedin.core

import app.lockedin.core.intelligence.AiRules
import app.lockedin.core.intelligence.AiTask
import app.lockedin.core.study.StudyAction
import app.lockedin.core.notes.Note
import app.lockedin.core.notes.NoteBook
import app.lockedin.core.notes.NoteRules
import app.lockedin.core.study.StudyContext
import app.lockedin.core.study.StudyContextCodec
import app.lockedin.core.study.StudyFocus
import app.lockedin.core.study.StudyMaterial
import app.lockedin.core.study.StudyPrompts
import app.lockedin.core.study.StudyTime

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

fun main() {
    // Each action sends the right task, and the material is always the text being studied.
    check("explain uses EXPLAIN", StudyPrompts.requestFor(StudyAction.EXPLAIN, "m").task == AiTask.EXPLAIN)
    check("summarise uses SUMMARISE", StudyPrompts.requestFor(StudyAction.SUMMARISE, "m").task == AiTask.SUMMARISE)
    check("quiz uses QUIZ", StudyPrompts.requestFor(StudyAction.QUIZ, "m").task == AiTask.QUIZ)
    check("test my understanding uses REVISION_QUESTIONS",
        StudyPrompts.requestFor(StudyAction.CHECK, "m").task == AiTask.REVISION_QUESTIONS)
    val examples = StudyPrompts.requestFor(StudyAction.EXAMPLES, "photosynthesis notes")
    check("examples ask a question about the material", examples.task == AiTask.ASK && examples.question == StudyPrompts.EXAMPLES_QUESTION)
    check("every action carries the material", StudyAction.values().all { StudyPrompts.requestFor(it, "abc").context == "abc" })
    check("every action passes the shared validation", StudyAction.values().all {
        AiRules.validate(StudyPrompts.requestFor(it, "some notes")) == null
    })

    // Questions are trimmed and need text.
    check("question is trimmed", StudyPrompts.questionRequest("m", "  why?  ").question == "why?")
    check("blank question is refused by the shared rules",
        AiRules.validate(StudyPrompts.questionRequest("m", "   ")) != null)

    // The AI sees the selection when there is one, otherwise the whole note.
    check("selection wins", StudyMaterial.choose("whole note", "  chosen part ") == "chosen part")
    check("no selection means whole note", StudyMaterial.choose("  whole note ", "   ") == "whole note")
    check("empty note and no selection gives empty material", StudyMaterial.choose("", "") == "")

    // Focus lines are short and carry the note's name.
    check("focus line names the note", StudyFocus.lineFor("Cells") == "Study: Cells")
    val longLine = StudyFocus.lineFor("x".repeat(300))
    check("focus line is capped", longLine.length <= StudyFocus.MAX_LINE_CHARS)

    // Workspace memory: round trip, answers only for their own note, damaged input is empty.
    val remembered = StudyContext(
        noteId = "n1",
        draftQuestion = "why is \"ATP\" needed?",
        answerNoteId = "n1",
        answer = "Line one\nline two \\ slash",
    )
    check("context round trip", StudyContextCodec.decode(StudyContextCodec.encode(remembered)) == remembered)
    check("answer shows for its own note", remembered.answerFor("n1") == remembered.answer)
    check("answer hidden for another note", remembered.answerFor("n2") == "")
    check("null context round trip", StudyContextCodec.decode(StudyContextCodec.encode(StudyContext())) == StudyContext())
    check("damaged context is empty", StudyContextCodec.decode("{oops") == StudyContext())
    check("blank context is empty", StudyContextCodec.decode(" ") == StudyContext())
    check("other version is empty", StudyContextCodec.decode("{\"v\":2}") == StudyContext())
    val huge = StudyContextCodec.decode(StudyContextCodec.encode(StudyContext(answer = "a".repeat(30_000))))
    check("saved answer is capped", huge.answer.length == StudyContextCodec.MAX_ANSWER_CHARS)

    // Word counts and relative times for the note list.
    check("blank body has no words", NoteRules.wordCount("   \n ") == 0)
    check("words split on any whitespace", NoteRules.wordCount("  one two\tthree\nfour ") == 4)
    val minute = 60_000L
    val now = 10_000_000_000L
    check("under a minute is just now", StudyTime.ago(now, now - 59_000L) == "just now")
    check("future times read as just now", StudyTime.ago(now, now + 5 * minute) == "just now")
    check("minutes", StudyTime.ago(now, now - 5 * minute) == "5m ago")
    check("hours", StudyTime.ago(now, now - 3 * 60 * minute) == "3h ago")
    check("yesterday", StudyTime.ago(now, now - 30 * 60 * minute) == "yesterday")
    check("days", StudyTime.ago(now, now - 3 * 24 * 60 * minute) == "3d ago")
    val titled = NoteBook(listOf(Note("a", "zebra", 1L), Note("b", "Apple", 2L), Note("c", "mango", 3L)))
    check("sorted by title ignores case", titled.sortedByTitle().map { it.title } == listOf("Apple", "mango", "zebra"))

    println("study checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed study check(s) failed")
}
