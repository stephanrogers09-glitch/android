package app.lockedin.phone

import android.app.KeyguardManager
import android.content.Context

object Allow {
    fun isAllowed(context: Context, packageName: String): Boolean {
        if (packageName == "app.lockedin.phone") return true
        if (packageName == "com.android.systemui") return true
        if (packageName == "com.android.phone") return true
        if (packageName == "com.android.server.telecom") return true
        val name = packageName.lowercase()
        if (name.contains("photo") || name.contains("gallery") || name.contains("album")) return false
        if (SessionStore.isPermitted(context, packageName)) return true
        return name.contains("dialer") ||
            name.contains("incall") ||
            name.contains("telecom") ||
            name.contains("emergency")
    }

    fun isWhatsAppCall(packageName: String?, className: String?): Boolean {
        val pkg = packageName?.lowercase().orEmpty()
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return false
        val cls = className?.lowercase().orEmpty()
        if (cls.contains("history") || cls.contains("conversation") || cls.contains("home")) return false
        return cls.contains("voip")
    }
}

fun isDeviceLocked(context: Context): Boolean {
    val guard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    return guard.isKeyguardLocked
}
