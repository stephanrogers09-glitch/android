package app.lockedin.phone

import android.app.KeyguardManager
import android.content.Context
import android.telecom.TelecomManager

object Allow {
    fun isAllowed(context: Context, packageName: String): Boolean {
        return AllowRules.isAllowed(
            packageName = packageName,
            defaultDialer = defaultDialer(context),
            permittedByUser = SessionStore.isPermitted(context, packageName),
        )
    }

    /** The package the system has set as the default phone app, or null if unknown. */
    private fun defaultDialer(context: Context): String? {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return telecom?.defaultDialerPackage
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
