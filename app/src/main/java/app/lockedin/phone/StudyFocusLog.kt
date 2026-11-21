package app.lockedin.phone

import android.content.Context

/**
 * Remembers which note a focus session was started from and how many minutes that note has had.
 * A session is credited only once its scheduled end has passed. Sessions started elsewhere are not logged.
 */
object StudyFocusLog {
    /** Intent extra naming the note a study screen should open. */
    const val EXTRA_NOTE_ID = "app.lockedin.phone.STUDY_NOTE"
    private const val FILE = "study-focus"
    private const val PENDING_NOTE = "pendingNote"
    private const val PENDING_MINUTES = "pendingMinutes"
    private const val PENDING_END = "pendingEnd"

    fun begin(context: Context, noteId: String, minutes: Int, endAt: Long) {
        prefs(context).edit()
            .putString(PENDING_NOTE, noteId)
            .putInt(PENDING_MINUTES, minutes)
            .putLong(PENDING_END, endAt)
            .apply()
    }

    /** Credits a finished study session to its note. Safe to call at any time. */
    fun settle(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = prefs(context)
        val noteId = prefs.getString(PENDING_NOTE, null) ?: return
        if (now < prefs.getLong(PENDING_END, 0L)) return
        val minutes = prefs.getInt(PENDING_MINUTES, 0)
        prefs.edit()
            .putInt(totalKey(noteId), prefs.getInt(totalKey(noteId), 0) + minutes)
            .remove(PENDING_NOTE)
            .remove(PENDING_MINUTES)
            .remove(PENDING_END)
            .apply()
    }

    /** The note the running session was started from, or null when no session runs or it was not from a note. */
    fun activeNoteId(context: Context): String? {
        if (!SessionStore.isActive(context)) return null
        return prefs(context).getString(PENDING_NOTE, null)
    }

    /** Drops any pending record. Call only after [settle], so a finished session is still credited. */
    fun forget(context: Context) {
        prefs(context).edit().remove(PENDING_NOTE).remove(PENDING_MINUTES).remove(PENDING_END).apply()
    }

    fun minutes(context: Context, noteId: String): Int = prefs(context).getInt(totalKey(noteId), 0)

    private fun totalKey(noteId: String) = "min-$noteId"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
