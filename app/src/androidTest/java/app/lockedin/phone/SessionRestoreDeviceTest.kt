package app.lockedin.phone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.lockedin.core.auth.AuthFailure
import app.lockedin.core.auth.AuthResult
import app.lockedin.core.auth.AuthSession
import app.lockedin.core.auth.SessionManager
import app.lockedin.core.auth.SessionRefresher
import app.lockedin.core.auth.TokenResult
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Restores a session from the real Keystore-backed store on a worker thread, the way the study screen
 * does, to prove the background read works on a device and that the token survives a "restart".
 */
@RunWith(AndroidJUnit4::class)
class SessionRestoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = System.currentTimeMillis()

    /** Never called in these tests: the session is still valid, so no refresh should happen. */
    private object NoRefresh : SessionRefresher {
        override fun refresh(refreshToken: String, nowMs: Long): AuthResult =
            AuthResult.Failed(AuthFailure.UNEXPECTED)
        override fun revoke(accessToken: String) = Unit
    }

    @Before
    fun start() {
        KeystoreSessionStore(context).delete()
    }

    @After
    fun finish() {
        KeystoreSessionStore(context).delete()
    }

    @Test
    fun sessionIsRestoredOnAWorkerThreadAfterRestart() {
        SessionManager(KeystoreSessionStore(context), NoRefresh)
            .begin(AuthSession("device-access", "device-refresh", now + 3_600_000L))

        val onMainThread = AtomicBoolean(true)
        val restored = AtomicBoolean(false)
        val token = arrayOfNulls<TokenResult>(1)
        val worker = Thread {
            onMainThread.set(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            // A fresh manager and store, as after the app process restarts.
            val restarted = SessionManager(KeystoreSessionStore(context), NoRefresh)
            restored.set(restarted.hasSession())
            token[0] = restarted.accessToken(now)
        }
        worker.start()
        worker.join()

        assertFalse("the Keystore read must not run on the main thread", onMainThread.get())
        assertTrue("session must be restored after restart", restored.get())
        assertEquals(TokenResult.Token("device-access"), token[0])
    }

    @Test
    fun signOutOnAWorkerThreadRemovesTheSession() {
        val manager = SessionManager(KeystoreSessionStore(context), NoRefresh)
        manager.begin(AuthSession("a", "r", now + 3_600_000L))
        val worker = Thread { manager.signOut() }
        worker.start()
        worker.join()
        assertFalse(SessionManager(KeystoreSessionStore(context), NoRefresh).hasSession())
    }
}
