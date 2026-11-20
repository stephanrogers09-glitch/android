package app.lockedin.core.notes

import app.lockedin.core.intelligence.AiWire
import app.lockedin.core.intelligence.Json

/** One study note. The body is what the student writes; the title is shown from its first line. */
data class Note(val id: String, val body: String, val updatedAtMs: Long) {
    val title: String get() = NoteRules.titleFor(body)
}

/** The student's notes, plus the one they had open last so they can carry on where they stopped. */
data class NoteBook(val notes: List<Note> = emptyList(), val lastOpenedId: String? = null) {
    fun sorted(): List<Note> = notes.sortedByDescending { it.updatedAtMs }

    fun find(id: String): Note? = notes.firstOrNull { it.id == id }

    fun lastOpened(): Note? = lastOpenedId?.let { find(it) }

    /** Adds a note, or replaces the note with the same id. A new note is refused when the book is full. */
    fun upsert(note: Note): NoteBook {
        if (find(note.id) == null && notes.size >= NoteRules.MAX_NOTES) return this
        val clean = note.copy(body = note.body.take(NoteRules.MAX_BODY_CHARS))
        return copy(notes = notes.filterNot { it.id == note.id } + clean)
    }

    fun remove(id: String): NoteBook = copy(
        notes = notes.filterNot { it.id == id },
        lastOpenedId = if (lastOpenedId == id) null else lastOpenedId,
    )

    /** Records the note the student is working on. Unknown ids are ignored. */
    fun opened(id: String): NoteBook = if (find(id) == null) this else copy(lastOpenedId = id)
}

object NoteRules {
    const val MAX_NOTES = 500
    const val MAX_BODY_CHARS = 50_000
    const val MAX_TITLE_CHARS = 60
    const val UNTITLED = "Untitled note"

    /** The first non-blank line, trimmed and shortened to fit a list row. Empty notes are untitled. */
    fun titleFor(body: String): String {
        val line = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return UNTITLED
        return if (line.length <= MAX_TITLE_CHARS) line else line.take(MAX_TITLE_CHARS - 1).trimEnd() + "…"
    }
}

/**
 * Versioned JSON for the notes file. Anything unreadable decodes to an empty book, so a damaged file
 * never stops the app from starting. Notes with a blank or duplicate id, or a missing field, are dropped.
 */
object NoteCodec {
    private const val VERSION = 1

    fun encode(book: NoteBook): String {
        val notes = book.notes.joinToString(",") { n ->
            "{\"id\":${AiWire.quote(n.id)},\"body\":${AiWire.quote(n.body)},\"updatedAtMs\":${n.updatedAtMs}}"
        }
        val last = book.lastOpenedId?.let { AiWire.quote(it) } ?: "null"
        return "{\"v\":$VERSION,\"lastOpenedId\":$last,\"notes\":[$notes]}"
    }

    fun decode(text: String?): NoteBook {
        if (text.isNullOrBlank()) return NoteBook()
        val root = Json.parseObjectOrNull(text) ?: return NoteBook()
        if (root["v"] != VERSION.toDouble()) return NoteBook()
        val raw = root["notes"] as? List<*> ?: return NoteBook()
        val seen = HashSet<String>()
        val notes = ArrayList<Note>()
        for (item in raw) {
            val id = (field(item, "id") as? String)?.trim().orEmpty()
            val body = field(item, "body") as? String ?: continue
            val updated = (field(item, "updatedAtMs") as? Double)?.toLong() ?: continue
            if (id.isEmpty() || !seen.add(id)) continue
            notes += Note(id, body.take(NoteRules.MAX_BODY_CHARS), updated)
            if (notes.size >= NoteRules.MAX_NOTES) break
        }
        val last = (root["lastOpenedId"] as? String)?.takeIf { wanted -> notes.any { it.id == wanted } }
        return NoteBook(notes, last)
    }

    private fun field(item: Any?, key: String): Any? =
        (item as? Map<*, *>)?.entries?.firstOrNull { it.key == key }?.value
}
