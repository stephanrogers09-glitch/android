package app.lockedin.phone

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

class CameraActivity : Activity() {
    private lateinit var stage: FrameLayout
    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private lateinit var lastShot: ImageView
    private lateinit var videoButton: ImageButton
    private lateinit var flashButton: ImageButton
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var recorder: MediaRecorder? = null
    private var videoUri: android.net.Uri? = null
    private var recording = false
    private var flash = false
    private var cameraId = ""
    private var sensor = 90
    private var previewSize = Size(1280, 720)
    private val busy = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Torch.off(this)
        stage = FrameLayout(this)
        stage.setBackgroundColor(Color.BLACK)
        stage.clipChildren = true
        texture = TextureView(this)
        stage.addView(
            texture,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        lastShot = ImageView(this)
        lastShot.scaleType = ImageView.ScaleType.CENTER_CROP
        val thumb = GradientDrawable()
        thumb.shape = GradientDrawable.OVAL
        thumb.setColor(Color.parseColor("#1A1A1A"))
        thumb.setStroke(dp(1), Color.parseColor("#66FFFFFF"))
        lastShot.background = thumb
        lastShot.clipToOutline = true
        lastShot.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        val shotParams = FrameLayout.LayoutParams(dp(54), dp(54), Gravity.TOP or Gravity.END)
        shotParams.topMargin = dp(28)
        shotParams.rightMargin = dp(20)
        stage.addView(lastShot, shotParams)

        val close = circleButton(R.drawable.ic_close, "Back") { finish() }
        val closeParams = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START)
        closeParams.topMargin = dp(28)
        closeParams.leftMargin = dp(20)
        stage.addView(close, closeParams)

        status = TextView(this)
        status.setTextColor(Color.parseColor("#E7E2DA"))
        status.gravity = Gravity.CENTER
        status.textSize = 13f
        val statusParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        statusParams.bottomMargin = dp(132)
        statusParams.leftMargin = dp(32)
        statusParams.rightMargin = dp(32)
        stage.addView(status, statusParams)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val rowParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        rowParams.bottomMargin = dp(36)
        stage.addView(row, rowParams)

        row.addView(shutter())
        videoButton = circleButton(R.drawable.ic_video, "Video") { toggleVideo() }
        row.addView(videoButton)
        flashButton = circleButton(R.drawable.ic_flash, "Flash") {
            flash = !flash
            status.text = if (flash) "Flash on" else ""
            startPreview()
        }
        row.addView(flashButton)

        setContentView(stage)
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                texture.scaleX = 1f
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                closeCamera()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun circleButton(icon: Int, name: String, onClick: () -> Unit): ImageButton {
        val button = ImageButton(this)
        button.setImageResource(icon)
        button.contentDescription = name
        button.scaleType = ImageView.ScaleType.CENTER_INSIDE
        button.setPadding(dp(12), dp(12), dp(12), dp(12))
        button.setColorFilter(Color.WHITE)
        val disk = GradientDrawable()
        disk.shape = GradientDrawable.OVAL
        disk.setColor(Color.parseColor("#66000000"))
        button.background = disk
        val params = LinearLayout.LayoutParams(dp(52), dp(52))
        params.marginStart = dp(10)
        params.marginEnd = dp(10)
        button.layoutParams = params
        button.setOnClickListener { onClick() }
        return button
    }

    private fun shutter(): View {
        val ring = GradientDrawable()
        ring.shape = GradientDrawable.OVAL
        ring.setStroke(dp(3), Color.WHITE)
        ring.setColor(Color.TRANSPARENT)
        val wrap = FrameLayout(this)
        wrap.background = ring
        wrap.contentDescription = "Photo"
        val inner = View(this)
        val fill = GradientDrawable()
        fill.shape = GradientDrawable.OVAL
        fill.setColor(Color.WHITE)
        inner.background = fill
        val gap = dp(7)
        wrap.addView(
            inner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(gap, gap, gap, gap) }
        )
        val params = LinearLayout.LayoutParams(dp(76), dp(76))
        params.marginStart = dp(16)
        params.marginEnd = dp(16)
        wrap.layoutParams = params
        wrap.setOnClickListener { takePhoto() }
        return wrap
    }

    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Camera permission is missing."
            return
        }
        thread = HandlerThread("locked-in-camera").also { it.start() }
        handler = Handler(thread!!.looper)
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.first()
            val chars = manager.getCameraCharacteristics(cameraId)
            sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            runOnUiThread { flashButton.visibility = if (hasFlash) View.VISIBLE else View.INVISIBLE }
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    startPreview()
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    camera = null
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    camera = null
                    runOnUiThread { status.text = "Camera is busy. Tap Back, then try again." }
                }
            }, handler)
        } catch (_: SecurityException) {
            status.text = "Camera permission is missing."
        } catch (_: CameraAccessException) {
            status.text = "Camera is busy. Tap Back, then try again."
        }
    }

    private fun startPreview() {
        val device = camera ?: return
        val surfaceTexture = texture.surfaceTexture ?: return
        session?.close()
        reader?.close()
        val size = choosePreview()
        previewSize = size
        runOnUiThread {
            texture.setTransform(Matrix())
            texture.scaleX = 1f
        }
        surfaceTexture.setDefaultBufferSize(size.width, size.height)
        val jpeg = jpegSize()
        reader = ImageReader.newInstance(jpeg.width, jpeg.height, ImageFormat.JPEG, 2)
        reader?.setOnImageAvailableListener({ images ->
            val image = images.acquireNextImage() ?: return@setOnImageAvailableListener
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            image.close()
            savePhoto(bytes)
        }, handler)
        val preview = Surface(surfaceTexture)
        val targets = listOf(preview, reader!!.surface)
        try {
            device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(capture: CameraCaptureSession) {
                    session = capture
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    request.addTarget(preview)
                    applyFlash(request)
                    capture.setRepeatingRequest(request.build(), null, handler)
                }

                override fun onConfigureFailed(capture: CameraCaptureSession) {
                    runOnUiThread { status.text = "Camera preview failed." }
                }
            }, handler)
        } catch (_: Exception) {
            runOnUiThread { status.text = "Camera preview failed." }
        }
    }

    private fun takePhoto() {
        val device = camera ?: return
        val capture = session ?: return
        val imageSurface = reader?.surface ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            request.addTarget(imageSurface)
            request.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
            applyFlash(request)
            capture.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: android.hardware.camera2.TotalCaptureResult
                ) {
                    busy.set(false)
                }
            }, handler)
        } catch (_: Exception) {
            busy.set(false)
            status.text = "Could not take the photo."
        }
    }

    private fun toggleVideo() {
        if (recording) stopVideo() else startVideo()
    }

    private fun startVideo() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 21)
            status.text = "Allow the microphone, then tap Video again."
            return
        }
        val device = camera ?: return
        val surfaceTexture = texture.surfaceTexture ?: return
        try {
            session?.close()
            val uri = newVideo() ?: return
            videoUri = uri
            val descriptor = contentResolver.openFileDescriptor(uri, "w") ?: return
            val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setVideoSize(1280, 720)
            next.setVideoFrameRate(30)
            next.setVideoEncodingBitRate(4_000_000)
            next.setOrientationHint(jpegOrientation())
            next.setOutputFile(descriptor.fileDescriptor)
            next.prepare()
            descriptor.close()
            recorder = next
            surfaceTexture.setDefaultBufferSize(1280, 720)
            previewSize = Size(1280, 720)
            runOnUiThread {
                texture.setTransform(Matrix())
                texture.scaleX = 1f
            }
            val preview = Surface(surfaceTexture)
            val recordSurface = next.surface
            device.createCaptureSession(listOf(preview, recordSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(capture: CameraCaptureSession) {
                    session = capture
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                    request.addTarget(preview)
                    request.addTarget(recordSurface)
                    applyFlash(request)
                    capture.setRepeatingRequest(request.build(), null, handler)
                    next.start()
                    recording = true
                    runOnUiThread {
                        videoButton.setColorFilter(Color.parseColor("#E23B3B"))
                        status.text = "Recording"
                    }
                }

                override fun onConfigureFailed(capture: CameraCaptureSession) {
                    failVideo()
                }
            }, handler)
        } catch (_: Exception) {
            failVideo()
        }
    }

    private fun stopVideo() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder?.release()
        recorder = null
        recording = false
        publishVideo()
        videoButton.setColorFilter(Color.WHITE)
        status.text = "Saved"
        startPreview()
    }

    private fun failVideo() {
        recorder?.release()
        recorder = null
        recording = false
        videoUri?.let { contentResolver.delete(it, null, null) }
        videoUri = null
        runOnUiThread {
            videoButton.setColorFilter(Color.WHITE)
            status.text = "Video did not start."
        }
        startPreview()
    }

    private fun savePhoto(bytes: ByteArray) {
        try {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "LockedIn_${System.currentTimeMillis()}.jpg")
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/LockedIn")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            val shot = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val stream = java.io.ByteArrayOutputStream()
            shot.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            val saved = stream.toByteArray()
            contentResolver.openOutputStream(uri)?.use { it.write(saved) }
            runOnUiThread {
                lastShot.setImageBitmap(shot)
                status.text = "Saved"
            }
        } catch (_: Exception) {
            runOnUiThread { status.text = "Could not save the photo." }
        }
    }

    private fun newVideo(): android.net.Uri? {
        val values = ContentValues()
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "LockedIn_${System.currentTimeMillis()}.mp4")
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/LockedIn")
            values.put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun publishVideo() {
        val uri = videoUri ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        }
        videoUri = null
    }

    private fun choosePreview(): Size {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val map = manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
        return sizes
            .filter { it.width <= 1920 && it.height <= 1080 }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.firstOrNull()
            ?: Size(1280, 720)
    }

    private fun jpegSize(): Size {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val map = manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        return sizes.maxByOrNull { it.width.toLong() * it.height } ?: Size(1920, 1080)
    }

    private fun jpegOrientation(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= 30) display?.rotation else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val device = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (sensor - device + 360) % 360
    }

    private fun applyFlash(request: CaptureRequest.Builder) {
        if (flash) {
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        } else {
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
    }

    private fun closeCamera() {
        if (recording) stopVideo()
        session?.close()
        session = null
        camera?.close()
        camera = null
        reader?.close()
        reader = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    @Deprecated("Return to the lock.")
    override fun onBackPressed() {
        finish()
    }
}
