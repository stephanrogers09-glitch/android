package app.lockedin.phone

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object Gate {
    fun adminComponent(context: Context): ComponentName {
        return ComponentName(context, AdminReceiver::class.java)
    }

    fun accessibilityOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { part ->
            part.startsWith("app.lockedin.phone/")
        }
    }

    fun adminOn(context: Context): Boolean {
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return manager.isAdminActive(adminComponent(context))
    }
}
