package app.lockedin.phone

import android.content.Context
import app.lockedin.core.auth.SessionManager

/** The one session manager for this process. Created on first use, with encrypted storage. */
object AppSession {
    @Volatile
    private var manager: SessionManager? = null

    fun manager(context: Context): SessionManager =
        manager ?: synchronized(this) {
            manager ?: SessionManager(
                KeystoreSessionStore(context.applicationContext),
                AuthClient(),
            ).also { manager = it }
        }
}
