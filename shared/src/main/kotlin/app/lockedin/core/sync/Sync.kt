package app.lockedin.core.sync

import app.lockedin.core.session.FocusSession

/** Identifies which write is newer when the same record changed on two devices. */
data class Revision(val updatedAtMs: Long, val deviceId: String)

enum class Winner { LOCAL, REMOTE }

object ConflictResolver {
    /**
     * Last writer wins by timestamp. Equal timestamps are broken by device id, so every device
     * reaches the same answer without coordination.
     */
    fun resolve(local: Revision, remote: Revision): Winner {
        if (local.updatedAtMs != remote.updatedAtMs) {
            return if (local.updatedAtMs > remote.updatedAtMs) Winner.LOCAL else Winner.REMOTE
        }
        return if (local.deviceId >= remote.deviceId) Winner.LOCAL else Winner.REMOTE
    }
}

/**
 * Local-first storage for session history. Implemented per platform (SharedPreferences or a
 * local database on Android, IndexedDB on web). The app must work with only this layer.
 */
interface SessionRepository {
    suspend fun save(session: FocusSession)
    suspend fun get(id: String): FocusSession?
    suspend fun recent(limit: Int): List<FocusSession>
}
