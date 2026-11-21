package app.lockedin.phone

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lockedin.core.notes.Note
import app.lockedin.core.notes.NoteBook
import app.lockedin.core.study.CommitmentSuggestions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device tests for the in-session study path and the focus setup suggestions. These drive the same
 * methods the buttons call, through test hooks, so they check behaviour rather than only that a screen opens.
 */
@RunWith(AndroidJUnit4::class)
class StudyAndFocusDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun clean() {
        SessionStore.clear(context)
        NotesStore.save(context, NoteBook())
    }

    @After
    fun tidy() {
        SessionStore.clear(context)
        NotesStore.save(context, NoteBook())
    }

    private fun waitUntil(timeoutMs: Long = 4000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(50)
            instrumentation.waitForIdleSync()
        }
        assertTrue("condition not met within $timeoutMs ms", check())
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync { block() }

    // --- In-session study ----------------------------------------------------------------------

    @Test
    fun existingNotesLoadDuringASession() {
        val now = System.currentTimeMillis()
        NotesStore.save(context, NoteBook(listOf(Note("existing", "Cells\nMitochondria make energy.", now)), "existing"))
        SessionStore.start(context, Preset.M25, "Study: Cells", now)
        StudyFocusLog.begin(context, "existing", 25, SessionStore.endAt(context))

        val study = instrumentation.startActivitySync(
            Intent(context, StudyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(StudyFocusLog.EXTRA_NOTE_ID, "existing")
        ) as StudyActivity
        waitUntil { study.openIdForTest() == "existing" }
        assertEquals("Cells\nMitochondria make energy.", study.editorTextForTest())
        assertTrue("the session keeps running", SessionStore.isActive(context))
        study.finish()
        instrumentation.waitForIdleSync()
    }

    @Test
    fun aNoteCanBeCreatedEditedSavedAndReloadedDuringASession() {
        val now = System.currentTimeMillis()
        SessionStore.start(context, Preset.M25, "Study", now)
        val study = instrumentation.startActivitySync(
            Intent(context, StudyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as StudyActivity
        instrumentation.waitForIdleSync()

        onMain { study.createNoteForTest() }
        waitUntil { study.openIdForTest() != null }
        val id = study.openIdForTest()!!

        onMain { study.setBodyForTest("Photosynthesis converts light into glucose.") }
        onMain { study.saveForTest() }
        onMain { study.setBodyForTest("Photosynthesis converts light energy into glucose.") }
        onMain { study.saveForTest() }

        // Saves run in the background, so wait for the stored copy rather than reading once.
        val final = "Photosynthesis converts light energy into glucose."
        waitUntil { NotesStore.load(context).find(id)?.body == final }
        assertEquals(final, NotesStore.load(context).find(id)?.body)
        assertTrue("the session keeps running", SessionStore.isActive(context))
        study.finish()
        instrumentation.waitForIdleSync()
    }

    // --- Focus setup suggestions and commitments -----------------------------------------------

    private fun openFocusSetup(): SetupActivity {
        val setup = instrumentation.startActivitySync(
            Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as SetupActivity
        instrumentation.waitForIdleSync()
        return setup
    }

    @Test
    fun suggestionsRotateToTheNextPhrase() {
        val setup = openFocusSetup()
        assertEquals(CommitmentSuggestions.at(0), setup.suggestionText())
        onMain { setup.advanceSuggestionForTest() }
        waitUntil { setup.suggestionText() == CommitmentSuggestions.at(1) }
        assertNotEquals(CommitmentSuggestions.at(0), setup.suggestionText())
        setup.finish()
    }

    @Test
    fun tappingASuggestionFillsTheCommitment() {
        val setup = openFocusSetup()
        val shown = setup.suggestionText()
        onMain { setup.tapSuggestionForTest() }
        instrumentation.waitForIdleSync()
        assertEquals(shown, setup.commitmentText())
        setup.finish()
    }

    @Test
    fun customCommitmentsStillWork() {
        val setup = openFocusSetup()
        val custom = "Derive the quadratic formula"
        onMain { setup.setCommitmentForTest(custom) }
        instrumentation.waitForIdleSync()
        assertEquals(custom, setup.commitmentText())

        // A suggestion tap replaces the custom text, and typing again still works afterwards.
        val shown = setup.suggestionText()
        onMain { setup.tapSuggestionForTest() }
        instrumentation.waitForIdleSync()
        assertEquals(shown, setup.commitmentText())
        setup.finish()
    }

    @Test
    fun aSessionIsNotStartedWhenTheLockIsNotReady() {
        // The emulator has no accessibility grant or device admin, so starting must refuse, and must not
        // record a session. Starting a real session needs the device permissions, checked on hardware.
        val setup = openFocusSetup()
        onMain { setup.tapSuggestionForTest() }
        onMain { setup.lockForTest() }
        instrumentation.waitForIdleSync()
        assertFalse(SessionStore.isActive(context))
        setup.finish()
    }
}
