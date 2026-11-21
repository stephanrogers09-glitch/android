package app.lockedin.phone

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Every top-level screen opens and closes without crashing, and the session fields the screens read are kept. */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun startWithoutSession() {
        SessionStore.clear(context)
    }

    @Test
    fun everyTopLevelScreenOpensAndCloses() {
        listOf(
            HomeActivity::class.java,
            SetupActivity::class.java,
            StudyActivity::class.java,
            LockActivity::class.java,
        ).forEach { screen ->
            val activity = instrumentation.startActivitySync(
                Intent(context, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            instrumentation.waitForIdleSync()
            assertNotNull("${screen.simpleName} did not open", activity)
            assertFalse("${screen.simpleName} closed on open", activity.isFinishing)
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun completionStateShowsWhenNoSessionIsRunning() {
        val activity = instrumentation.startActivitySync(
            Intent(context, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        instrumentation.waitForIdleSync()
        assertFalse(SessionStore.isActive(context))
        assertFalse(activity.isFinishing)
        activity.finish()
        instrumentation.waitForIdleSync()
    }

    @Test
    fun sessionRecordsItsStartAndPresetForTheSummary() {
        val now = System.currentTimeMillis()
        SessionStore.start(context, Preset.M25, "Read chapter three", now)
        assertEquals(now, SessionStore.startedAt(context))
        assertEquals("25", SessionStore.presetId(context))
        assertTrue(SessionStore.isActive(context, now + 1_000))

        SessionStore.clear(context)
        assertEquals(0L, SessionStore.startedAt(context))
        assertNull(SessionStore.presetId(context))
    }

    @Test
    fun studyFocusIsCreditedOnlyAfterItsEnd() {
        val start = 1_000_000L
        StudyFocusLog.begin(context, "note-focus-test", 2, start + 120_000L)
        StudyFocusLog.settle(context, start + 60_000L)
        assertEquals(0, StudyFocusLog.minutes(context, "note-focus-test"))
        StudyFocusLog.settle(context, start + 120_000L)
        assertEquals(2, StudyFocusLog.minutes(context, "note-focus-test"))
        StudyFocusLog.settle(context, start + 240_000L)
        assertEquals(2, StudyFocusLog.minutes(context, "note-focus-test"))
    }

    @Test
    fun studyOpensFromTheLockWhileTheSessionKeepsRunning() {
        val now = System.currentTimeMillis()
        SessionStore.start(context, Preset.M25, "Study: Biology", now)
        StudyFocusLog.begin(context, "note-during-session", 25, SessionStore.endAt(context))
        val lock = instrumentation.startActivitySync(
            Intent(context, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        instrumentation.waitForIdleSync()
        assertEquals("note-during-session", StudyFocusLog.activeNoteId(context))

        val study = instrumentation.startActivitySync(
            Intent(context, StudyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(StudyFocusLog.EXTRA_NOTE_ID, "note-during-session")
        )
        instrumentation.waitForIdleSync()
        assertFalse("study screen closed on open", study.isFinishing)
        assertTrue("the session must keep running inside LockedIn", SessionStore.isActive(context))
        assertTrue("LockedIn itself stays allowed", Allow.isAllowed(context, context.packageName))

        study.finish()
        lock.finish()
        instrumentation.waitForIdleSync()
    }

    @Test
    fun callsStayAllowedAndOtherAppsStayBlockedDuringASession() {
        SessionStore.start(context, Preset.M25, "Study", System.currentTimeMillis())
        assertTrue(Allow.isAllowed(context, "com.google.android.dialer"))
        assertTrue(Allow.isAllowed(context, "com.android.incallui"))
        assertTrue(Allow.isAllowed(context, "com.android.phone"))
        assertFalse(Allow.isAllowed(context, "com.example.not.installed.social"))
        assertFalse(Allow.isAllowed(context, "com.example.gallery.photos"))
    }

    @Test
    fun aRunningSessionIsNeverReplacedFromFocusSetup() {
        val now = System.currentTimeMillis()
        SessionStore.start(context, Preset.M25, "Original", now)
        val end = SessionStore.endAt(context)
        instrumentation.startActivitySync(
            Intent(context, SetupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SetupActivity.EXTRA_PRESET, Preset.M2.id)
                .putExtra(SetupActivity.EXTRA_AUTOSTART, true)
        )
        instrumentation.waitForIdleSync()
        assertEquals(end, SessionStore.endAt(context))
        assertEquals("Original", SessionStore.line(context))
    }

    @Test
    fun aSessionFinishedAfterStudyCompletesAndIsCredited() {
        val now = System.currentTimeMillis()
        SessionStore.start(context, Preset.M2, "Study: Chemistry", now - 300_000L)
        StudyFocusLog.begin(context, "note-complete", 2, SessionStore.endAt(context))
        assertFalse(SessionStore.isActive(context))
        assertNull(StudyFocusLog.activeNoteId(context))
        StudyFocusLog.settle(context, now)
        assertEquals(2, StudyFocusLog.minutes(context, "note-complete"))

        val lock = instrumentation.startActivitySync(
            Intent(context, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        instrumentation.waitForIdleSync()
        assertFalse(lock.isFinishing)
        lock.finish()
        instrumentation.waitForIdleSync()
        SessionStore.clear(context)
    }

    @Test
    fun theDeviceKeyboardIsAllowedSoTypingDoesNotLeaveLockedIn() {
        SessionStore.start(context, Preset.M25, "Study", System.currentTimeMillis())
        val keyboard = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        )?.substringBefore('/')
        if (keyboard != null) assertTrue("the default keyboard must be allowed", Allow.isAllowed(context, keyboard))
        assertFalse(Allow.isAllowed(context, "com.example.not.installed.social"))
        SessionStore.clear(context)
    }
}
