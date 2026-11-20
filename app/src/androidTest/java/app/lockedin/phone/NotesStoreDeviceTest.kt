package app.lockedin.phone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lockedin.core.notes.Note
import app.lockedin.core.notes.NoteBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Notes survive a restart: they are written to private storage and read back intact on the device. */
@RunWith(AndroidJUnit4::class)
class NotesStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.filesDir, "study-notes.json")

    @Before
    fun clean() {
        file.delete()
    }

    @After
    fun tidy() {
        file.delete()
    }

    @Test
    fun notesAndTheResumePointSurviveARestart() {
        val book = NoteBook(
            notes = listOf(Note("n1", "Fictional note: the glass moths\nline two", 10L)),
            lastOpenedId = "n1",
        )
        NotesStore.save(context, book)
        assertEquals(book, NotesStore.load(context))
        assertTrue("notes must be in private storage", file.absolutePath.startsWith(context.filesDir.absolutePath))
    }

    @Test
    fun aDamagedFileLoadsAsAnEmptyNotebook() {
        file.writeText("{this is not the notes file")
        assertEquals(NoteBook(), NotesStore.load(context))
    }

    @Test
    fun noFileLoadsAsAnEmptyNotebook() {
        assertEquals(NoteBook(), NotesStore.load(context))
    }
}
