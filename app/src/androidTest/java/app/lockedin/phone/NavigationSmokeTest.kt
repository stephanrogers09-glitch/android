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
}
