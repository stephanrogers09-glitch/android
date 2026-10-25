package app.lockedin.phone

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {
    private var lastBounce = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!SessionStore.isActive(this)) return
        if (isDeviceLocked(this) || CallState.active) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        Foreground.note(pkg, cls)
        val whatsApp = pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b"
        if (Allow.isWhatsAppCall(pkg, cls) || (Foreground.whatsAppCall && whatsApp)) return
        if (cls.lowercase().contains("incall") || cls.lowercase().contains("incoming")) return
        if (Allow.isAllowed(this, pkg)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBounce < 400) return
        lastBounce = now
        val launch = Intent(this, LockActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
        try {
            startActivity(launch)
        } catch (_: Exception) {
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (SessionStore.isActive(this)) Ping.quit(this)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        CallState.watch(this)
        if (!SessionStore.isActive(this) || isDeviceLocked(this) || CallState.active) return
        val launch = Intent(this, LockActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        try {
            startActivity(launch)
        } catch (_: Exception) {
        }
    }
}
