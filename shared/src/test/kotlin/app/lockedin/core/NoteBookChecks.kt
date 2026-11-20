package app.lockedin.core

import app.lockedin.core.notes.Note
import app.lockedin.core.notes.NoteBook
import app.lockedin.core.notes.NoteCodec
import app.lockedin.core.notes.NoteRules

private var passed = 0
private var failed = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passed++ else { failed++; println("FAIL: $name") }
}

fun main() {
    // Titles come from the first non-blank line.
    check("untitled when empty", NoteRules.titleFor("  \n\n ") == NoteRules.UNTITLED)
    check("title skips blank lines and trims", NoteRules.titleFor("\n  Photosynthesis  \nlight reactions") == "Photosynthesis")
    val long = "x".repeat(200)
    check("long title is shortened", NoteRules.titleFor(long).length == NoteRules.MAX_TITLE_CHARS)

    // Upsert adds, then replaces by id, and keeps the book free of duplicates.
    val a = Note("a", "First", 100L)
    val book = NoteBook().upsert(a).upsert(Note("b", "Second", 200L)).upsert(a.copy(body = "Edited", updatedAtMs = 300L))
    check("upsert keeps two notes", book.notes.size == 2)
    check("upsert replaces the body", book.find("a")?.body == "Edited")
    check("sorted newest first", book.sorted().map { it.id } == listOf("a", "b"))

    // Opening records the note; unknown ids are ignored.
    val opened = book.opened("b")
    check("opened sets last note", opened.lastOpened()?.id == "b")
    check("opening an unknown id is ignored", book.opened("missing").lastOpenedId == null)

    // Removing a note clears the resume pointer when it was the open note.
    val removed = opened.remove("b")
    check("remove deletes the note", removed.find("b") == null)
    check("remove clears the resume pointer", removed.lastOpenedId == null)

    // The book refuses new notes beyond the limit, but still edits existing ones.
    var full = NoteBook()
    for (i in 0 until NoteRules.MAX_NOTES) full = full.upsert(Note("n$i", "t", i.toLong()))
    check("full book refuses a new note", full.upsert(Note("extra", "t", 1L)).notes.size == NoteRules.MAX_NOTES)
    check("full book still edits a note", full.upsert(Note("n0", "changed", 9L)).find("n0")?.body == "changed")

    // Bodies are capped.
    val capped = NoteBook().upsert(Note("c", "y".repeat(NoteRules.MAX_BODY_CHARS + 500), 1L))
    check("body is capped", capped.find("c")?.body?.length == NoteRules.MAX_BODY_CHARS)

    // Round trip keeps quotes, newlines, unicode and the resume pointer.
    val tricky = NoteBook(
        notes = listOf(Note("q1", "He said \"hi\"\nline two\ttab \\ slash é", 42L), Note("q2", "", 7L)),
        lastOpenedId = "q1",
    )
    val decoded = NoteCodec.decode(NoteCodec.encode(tricky))
    check("codec round trip", decoded == tricky)

    // Damaged or foreign input becomes an empty book, never a crash.
    check("null file is empty", NoteCodec.decode(null) == NoteBook())
    check("blank file is empty", NoteCodec.decode("   ") == NoteBook())
    check("corrupt JSON is empty", NoteCodec.decode("{not json") == NoteBook())
    check("other version is empty", NoteCodec.decode("{\"v\":9,\"notes\":[]}") == NoteBook())
    check("non-object is empty", NoteCodec.decode("[1,2]") == NoteBook())

    // Bad entries are dropped, good ones kept.
    val mixed = "{\"v\":1,\"lastOpenedId\":\"ok\",\"notes\":[" +
        "{\"id\":\"ok\",\"body\":\"keep\",\"updatedAtMs\":5}," +
        "{\"id\":\"\",\"body\":\"blank id\",\"updatedAtMs\":5}," +
        "{\"id\":\"ok\",\"body\":\"duplicate\",\"updatedAtMs\":6}," +
        "{\"id\":\"nobody\",\"updatedAtMs\":5}," +
        "{\"id\":\"notime\",\"body\":\"x\"}," +
        "\"not an object\"]}"
    val fromMixed = NoteCodec.decode(mixed)
    check("mixed file keeps only the valid note", fromMixed.notes.map { it.id } == listOf("ok"))
    check("mixed file keeps the first duplicate", fromMixed.find("ok")?.body == "keep")
    check("resume pointer kept when its note survives", fromMixed.lastOpenedId == "ok")

    val dangling = NoteCodec.decode("{\"v\":1,\"lastOpenedId\":\"gone\",\"notes\":[]}")
    check("resume pointer to a missing note is cleared", dangling.lastOpenedId == null)

    println("note book checks: $passed passed, $failed failed")
    if (failed > 0) throw IllegalStateException("$failed note check(s) failed")
}
