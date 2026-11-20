package app.lockedin.phone

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs on a device or emulator, against the real Android Keystore. Checks that the session record is
 * encrypted at rest, survives a new store instance (an app restart), and that corrupt or tampered data
 * is discarded instead of crashing.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.noBackupFilesDir, KeystoreSessionStore.FILE_NAME)

    @Before
    fun start() {
        KeystoreSessionStore(context).delete()
    }

    @After
    fun finish() {
        KeystoreSessionStore(context).delete()
    }

    @Test
    fun roundTripReturnsTheRecord() {
        KeystoreSessionStore(context).write("{\"v\":1}")
        assertEquals("{\"v\":1}", KeystoreSessionStore(context).read())
    }

    @Test
    fun recordSurvivesANewStoreInstance() {
        KeystoreSessionStore(context).write("restart-record")
        // A new instance is what the app gets after a restart.
        assertEquals("restart-record", KeystoreSessionStore(context).read())
    }

    @Test
    fun tokenIsNotStoredAsPlaintext() {
        val marker = "needle-token-7f3a9c"
        KeystoreSessionStore(context).write("{\"access\":\"$marker\"}")
        val onDisk = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("access token must not appear in the stored file", onDisk.contains(marker))
    }

    @Test
    fun recordIsStoredInTheNoBackupDirectory() {
        KeystoreSessionStore(context).write("x")
        assertTrue(file.exists())
        assertEquals(context.noBackupFilesDir, file.parentFile)
    }

    @Test
    fun deleteRemovesTheRecord() {
        KeystoreSessionStore(context).write("x")
        KeystoreSessionStore(context).delete()
        assertNull(KeystoreSessionStore(context).read())
        assertFalse(file.exists())
    }

    @Test
    fun corruptFileIsDiscarded() {
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(KeystoreSessionStore(context).read())
        assertFalse("corrupt record must be deleted", file.exists())
    }

    @Test
    fun tamperedCiphertextIsDiscarded() {
        KeystoreSessionStore(context).write("secret")
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
        assertNull(KeystoreSessionStore(context).read())
        assertFalse("tampered record must be deleted", file.exists())
    }
}
