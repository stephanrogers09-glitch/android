package app.lockedin.phone

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.lockedin.core.auth.SessionStore
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the session record encrypted with AES-256-GCM. The key lives in the Android Keystore and
 * cannot be exported. The ciphertext is written to noBackupFilesDir, which is excluded from cloud
 * backup and device transfer. Nothing sensitive is written to SharedPreferences or to plain files.
 *
 * Failure policy: a record that cannot be decrypted (for example after the key is lost) is deleted,
 * and the student signs in again. A failed write is not fatal: the session still works until restart.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val file = File(context.noBackupFilesDir, FILE_NAME)

    override fun read(): String? {
        if (!file.exists()) return null
        return try {
            val blob = file.readBytes()
            if (blob.size <= IV_BYTES) {
                file.delete()
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_BYTES)))
            String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // Tampered data, or a key that no longer matches. Start signed out.
            file.delete()
            null
        } catch (e: IOException) {
            null
        }
    }

    override fun write(record: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(record.toByteArray(Charsets.UTF_8))
            // Write to a temporary file and rename, so a crash never leaves a half-written record.
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeBytes(cipher.iv + ciphertext)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: GeneralSecurityException) {
            file.delete()
        } catch (e: IOException) {
            file.delete()
        }
    }

    override fun delete() {
        file.delete()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val FILE_NAME = "session.enc"
        const val KEY_ALIAS = "lockedin.session.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
