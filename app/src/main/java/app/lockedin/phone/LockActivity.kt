package app.lockedin.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher

class LockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var released = false
    private var openingChild = false
    private lateinit var root: FrameLayout
    private lateinit var title: TextView
    private lateinit var sentence: TextView
    private lateinit var status: TextView
    private lateinit var actions: LinearLayout
    private lateinit var callButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var lightButton: ImageButton
    private lateinit var doneButton: TextView

    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { }
        }

        root = FrameLayout(this)
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.setPadding(dp(24), dp(56), dp(24), dp(36))
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Quiet by design: the state, the commitment and the lock status. No timer is shown.
        title = TextView(this)
        title.gravity = Gravity.CENTER
        title.textSize = 40f
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        column.addView(title)

        sentence = TextView(this)
        sentence.gravity = Gravity.CENTER
        sentence.textSize = 20f
        sentence.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        sentence.setLineSpacing(dp(4).toFloat(), 1f)
        sentence.setPadding(dp(12), dp(20), dp(12), 0)
        column.addView(sentence, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        status = TextView(this)
        status.gravity = Gravity.CENTER
        status.typeface = Typeface.MONOSPACE
        status.letterSpacing = 0.14f
        status.textSize = 11f
        status.setPadding(0, dp(18), 0, dp(28))
        column.addView(status)

        actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        callButton = iconButton(R.drawable.ic_call, "Call") {
            openingChild = true
            startActivity(Intent(this, CallActivity::class.java))
        }
        cameraButton = iconButton(R.drawable.ic_camera, "Camera") {
            openingChild = true
            try {
                Phone.openCamera(this)
            } catch (_: Exception) {
                openingChild = false
            }
        }
        lightButton = iconButton(R.drawable.ic_flash, "Light") {
            Torch.toggle(this)
            paintLight()
        }
        actions.addView(callButton)
        actions.addView(cameraButton)
        actions.addView(lightButton)
        column.addView(actions)

        doneButton = pill("Done", filled = true).apply {
            textSize = 16f
            setPadding(dp(56), dp(16), dp(56), dp(16))
            setOnClickListener {
                SessionStore.clear(this@LockActivity)
                startActivity(
                    Intent(this@LockActivity, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                finish()
            }
        }
        column.addView(doneButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        openingChild = false
        CallState.watch(this)
        hideSystemUi()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onUserLeaveHint() {
        if (openingChild || !SessionStore.isActive(this) || isDeviceLocked(this)) return
        handler.postDelayed({
            if (openingChild || CallState.active || Foreground.whatsAppCall || isDeviceLocked(this) || !SessionStore.isActive(this)) return@postDelayed
            val top = Foreground.packageName
            if (top != null && Allow.isAllowed(this, top)) return@postDelayed
            if (Allow.isWhatsAppCall(top, Foreground.className)) return@postDelayed
            val launch = Intent(this, LockActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            startActivity(launch)
        }, 500)
    }

    @Deprecated("Back is swallowed for the whole lock.")
    override fun onBackPressed() {
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val active = SessionStore.isActive(this, now)
        released = !active
        paint()
        if (active) {
            title.text = "Locked in"
            sentence.text = SessionStore.line(this)
            status.text = if (Gate.accessibilityOn(this)) "LOCK ON  ·  CALLS OPEN" else "LOCK OFF  ·  CHECK SETUP IN FOCUS"
            status.visibility = View.VISIBLE
            actions.visibility = View.VISIBLE
            doneButton.visibility = View.GONE
        } else {
            title.text = "Session complete"
            sentence.text = ""
            status.visibility = View.GONE
            actions.visibility = View.GONE
            doneButton.visibility = View.VISIBLE
            Torch.off(this)
            paintLight()
        }
    }

    private fun paint() {
        root.setBackgroundColor(Color.parseColor(Ink.BLACK))
        title.setTextColor(Color.parseColor(Ink.TEXT))
        sentence.setTextColor(Color.parseColor(Ink.TEXT))
        status.setTextColor(Color.parseColor(Ink.FAINT))
        paintLight()
    }

    private fun paintLight() {
        val on = Torch.isOn()
        val ink = Color.parseColor(if (on) "#F3E2B8" else Ink.TEXT)
        lightButton.setColorFilter(ink)
        val disk = lightButton.background as GradientDrawable
        disk.setStroke(dp(1), Color.parseColor(if (on) "#F3E2B8" else Ink.HAIRLINE))
        disk.setColor(if (on) Color.parseColor("#22F3E2B8") else Color.TRANSPARENT)
    }

    private fun iconButton(icon: Int, name: String, onClick: () -> Unit): ImageButton {
        val button = ImageButton(this)
        button.setImageResource(icon)
        button.contentDescription = name
        button.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        button.setPadding(dp(16), dp(16), dp(16), dp(16))
        button.setColorFilter(Color.parseColor(Ink.TEXT))
        val disk = GradientDrawable()
        disk.shape = GradientDrawable.OVAL
        disk.setColor(Color.TRANSPARENT)
        disk.setStroke(dp(1), Color.parseColor(Ink.HAIRLINE))
        button.background = disk
        val params = LinearLayout.LayoutParams(dp(68), dp(68))
        params.marginStart = dp(16)
        params.marginEnd = dp(16)
        button.layoutParams = params
        button.setOnClickListener { onClick() }
        return button
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }
}
