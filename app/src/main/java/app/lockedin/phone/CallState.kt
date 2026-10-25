package app.lockedin.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

object Foreground {
    @Volatile var packageName: String? = null
    @Volatile var className: String? = null
    @Volatile var whatsAppCall = false

    fun note(packageName: String, className: String) {
        this.packageName = packageName
        this.className = className
        val cls = className.lowercase()
        if (Allow.isWhatsAppCall(packageName, className)) {
            whatsAppCall = true
            return
        }
        val whatsApp = packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"
        val dialog = cls.startsWith("android.") || cls.contains("dialog") || cls.contains("popup") || cls.contains("sheet")
        if (whatsApp && dialog && whatsAppCall) return
        whatsAppCall = false
    }
}

object CallState {
    @Volatile var active = false

    private var watching = false
    private val handler = Handler(Looper.getMainLooper())

    fun watch(context: Context) {
        if (watching) return
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        watching = true
        val app = context.applicationContext
        val manager = app.getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            manager.registerTelephonyCallback(
                app.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        changed(app, state)
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            manager.listen(
                object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        changed(app, state)
                    }
                },
                PhoneStateListener.LISTEN_CALL_STATE
            )
        }
    }

    private fun changed(context: Context, state: Int) {
        val was = active
        active = state != TelephonyManager.CALL_STATE_IDLE
        if (was && !active) {
            handler.postDelayed({
                if (active || !SessionStore.isActive(context)) return@postDelayed
                val launch = Intent(context, LockActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                try {
                    context.startActivity(launch)
                } catch (_: Exception) {
                }
            }, 700)
        }
    }
}
