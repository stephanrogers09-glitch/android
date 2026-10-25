package app.lockedin.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri

object Phone {
    fun place(context: Context, number: String) {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isEmpty()) return
        val uri = Uri.parse("tel:$clean")
        val granted = context.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
        remember(context, intent)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openCamera(context: Context) {
        Torch.off(context)
        val intent = Intent(context, CameraActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun remember(context: Context, intent: Intent) {
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = resolved?.activityInfo?.packageName ?: return
        SessionStore.allow(context, pkg)
    }
}

object Torch {
    private var lit = false

    fun isOn(): Boolean = lit

    fun toggle(context: Context): Boolean {
        lit = !lit
        if (!set(context, lit)) {
            lit = false
            return false
        }
        return true
    }

    fun off(context: Context) {
        lit = false
        set(context, false)
    }

    private fun set(context: Context, enabled: Boolean): Boolean {
        return try {
            val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cameras.cameraIdList.firstOrNull { cameraId ->
                cameras.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cameras.setTorchMode(id, enabled)
            true
        } catch (_: Exception) {
            false
        }
    }
}
