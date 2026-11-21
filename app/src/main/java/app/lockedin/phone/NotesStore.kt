package app.lockedin.phone

import android.content.Context
import app.lockedin.core.notes.NoteBook
import app.lockedin.core.notes.NoteCodec
import java.io.File

/**
 * Keeps the study notes in one file in this app's private storage. The file is not backed up
 * (allowBackup is off) and is never sent anywhere except when the student asks the AI helper about
 * a note. Reads and writes are called from a background thread, never the main thread.
 */
object NotesStore {
    private const val FILE = "study-notes.json"

    @Synchronized
    fun load(context: Context): NoteBook {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return NoteBook()
        return try {
            NoteCodec.decode(file.readText(Charsets.UTF_8))
        } catch (e: java.io.IOException) {
            NoteBook()
        }
    }

    @Synchronized
    fun save(context: Context, book: NoteBook) {
        val target = File(context.filesDir, FILE)
        val temp = File(context.filesDir, "$FILE.tmp")
        temp.writeText(NoteCodec.encode(book), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }
}
