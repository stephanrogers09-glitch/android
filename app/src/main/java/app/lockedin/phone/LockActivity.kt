package app.lockedin.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class LockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var released = false
    private var openingChild = false
    private lateinit var root: FrameLayout
    private lateinit var clock: TextView
    private lateinit var word: TextView
    private lateinit var backAt: TextView
    private lateinit var sentence: TextView
    private lateinit var callButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var lightButton: ImageButton
    private lateinit var doneButton: Button

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
        column.setPadding(dp(24), dp(72), dp(24), dp(36))
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        clock = TextView(this)
        clock.textSize = 86f
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        clock.gravity = Gravity.CENTER
        column.addView(clock)

        word = TextView(this)
        word.text = "LOCKED IN"
        word.gravity = Gravity.CENTER
        word.letterSpacing = 0.28f
        word.setPadding(0, dp(20), 0, 0)
        column.addView(word)

        backAt = TextView(this)
        backAt.gravity = Gravity.CENTER
        backAt.setPadding(0, dp(8), 0, 0)
        column.addView(backAt)

        sentence = TextView(this)
        sentence.gravity = Gravity.CENTER
        sentence.textSize = 20f
        sentence.setLineSpacing(dp(4).toFloat(), 1f)
        sentence.setPadding(dp(20), 0, dp(20), 0)
        val sentenceParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        column.addView(sentence, sentenceParams)
        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        val actions = LinearLayout(this)
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

        doneButton = Button(this)
        doneButton.text = "Done"
        doneButton.setOnClickListener {
            SessionStore.clear(this)
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        column.addView(doneButton)

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
        clock.text = formatClock(now)
        if (active) {
            word.text = "LOCKED IN"
            backAt.text = "until ${formatBackAt(SessionStore.endAt(this))}"
            sentence.text = SessionStore.line(this)
            callButton.visibility = View.VISIBLE
            cameraButton.visibility = View.VISIBLE
            lightButton.visibility = View.VISIBLE
            doneButton.visibility = View.GONE
        } else {
            word.text = "COMPLETE"
            backAt.text = ""
            sentence.text = SessionStore.line(this)
            callButton.visibility = View.GONE
            cameraButton.visibility = View.GONE
            lightButton.visibility = View.GONE
            doneButton.visibility = View.VISIBLE
            Torch.off(this)
            paintLight()
        }
        paint()
    }

    private fun paint() {
        root.setBackgroundColor(Color.parseColor(COLOR_BG))
        val foreground = Color.parseColor(COLOR_FG)
        val muted = Color.parseColor(COLOR_MUTED)
        clock.setTextColor(foreground)
        word.setTextColor(foreground)
        word.textSize = 12f
        backAt.setTextColor(muted)
        backAt.textSize = 14f
        sentence.setTextColor(Color.parseColor("#D9D3C9"))
        paintLight()
        doneButton.outline(this, foreground)
        doneButton.fillWidth()
    }

    private fun paintLight() {
        val on = Torch.isOn()
        val ink = Color.parseColor(if (on) "#F3E2B8" else COLOR_FG)
        lightButton.setColorFilter(ink)
        val disk = lightButton.background as GradientDrawable
        disk.setStroke(dp(1), Color.parseColor(if (on) "#F3E2B8" else "#343434"))
        disk.setColor(if (on) Color.parseColor("#22F3E2B8") else Color.TRANSPARENT)
    }

    private fun iconButton(icon: Int, name: String, onClick: () -> Unit): ImageButton {
        val button = ImageButton(this)
        button.setImageResource(icon)
        button.contentDescription = name
        button.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        button.setPadding(dp(16), dp(16), dp(16), dp(16))
        button.setColorFilter(Color.parseColor(COLOR_FG))
        val disk = GradientDrawable()
        disk.shape = GradientDrawable.OVAL
        disk.setColor(Color.TRANSPARENT)
        disk.setStroke(dp(1), Color.parseColor("#343434"))
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
