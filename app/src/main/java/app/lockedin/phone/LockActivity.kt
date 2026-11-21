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

/**
 * The lock screen, in the original LockedIn character: a large clock, the state, when it ends, the
 * commitment, and three quiet controls. Nothing explains the lock; the symbols do that.
 */
class LockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var released = false
    private var openingChild = false
    private lateinit var root: FrameLayout
    private lateinit var clock: TextView
    private lateinit var label: TextView
    private lateinit var until: TextView
    private lateinit var sentence: TextView
    private lateinit var route: TextView
    private lateinit var actions: LinearLayout
    private lateinit var lockBroken: TextView
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
        column.setPadding(dp(24), dp(48), dp(24), dp(36))
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        clock = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 96f
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        }
        column.addView(clock)

        label = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            letterSpacing = 0.42f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(0, dp(18), 0, 0)
        }
        column.addView(label)

        until = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 17f
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            setPadding(0, dp(8), 0, 0)
        }
        column.addView(until)

        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        sentence = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 30f
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(16), 0, dp(16), 0)
        }
        column.addView(sentence, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        route = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            isClickable = true
            setOnClickListener { openStudy() }
        }
        column.addView(route, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

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
        column.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(26)
        })

        // Shown only if the lock itself is not running. Otherwise the screen says nothing about it.
        lockBroken = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            text = "Lock is off. Open Focus to turn it on."
            setPadding(0, dp(14), 0, 0)
            visibility = View.GONE
        }
        column.addView(lockBroken)

        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        doneButton = pill("Done").apply {
            textSize = 15f
            setPadding(dp(56), dp(14), dp(56), dp(14))
            setOnClickListener {
                SessionStore.clear(this@LockActivity)
                startActivity(
                    Intent(this@LockActivity, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                finish()
            }
        }
        column.addView(doneButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

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

    /** Study is inside LockedIn, so it is an allowed screen. The flag stops the leave-hint relaunch for this step. */
    private fun openStudy() {
        openingChild = true
        startActivity(
            Intent(this, StudyActivity::class.java)
                .putExtra(StudyFocusLog.EXTRA_NOTE_ID, StudyFocusLog.activeNoteId(this))
        )
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val active = SessionStore.isActive(this, now)
        released = !active
        paint()
        clock.text = formatClock(now)
        if (active) {
            label.text = "LOCKED IN"
            until.text = "until ${formatBackAt(SessionStore.endAt(this))}"
            until.visibility = View.VISIBLE
            sentence.text = SessionStore.line(this)
            sentence.visibility = View.VISIBLE
            route.text = if (StudyFocusLog.activeNoteId(this) != null) "Back to your note" else "Open notes"
            route.visibility = View.VISIBLE
            actions.visibility = View.VISIBLE
            lockBroken.visibility = if (Gate.accessibilityOn(this)) View.GONE else View.VISIBLE
            doneButton.visibility = View.GONE
        } else {
            label.text = "SESSION COMPLETE"
            until.visibility = View.GONE
            sentence.visibility = View.GONE
            route.visibility = View.GONE
            actions.visibility = View.GONE
            lockBroken.visibility = View.GONE
            doneButton.visibility = View.VISIBLE
            Torch.off(this)
            paintLight()
        }
    }

    private fun paint() {
        root.setBackgroundColor(Color.parseColor(Ink.BLACK))
        clock.setTextColor(Color.parseColor(Ink.PAPER))
        label.setTextColor(Color.parseColor(Ink.PAPER))
        until.setTextColor(Color.parseColor(Ink.MUTED))
        sentence.setTextColor(Color.parseColor(Ink.PAPER))
        route.setTextColor(Color.parseColor(Ink.PAPER))
        route.background = surface(Ink.BLACK, 50, Ink.RING)
        lockBroken.setTextColor(Color.parseColor(Ink.MUTED))
        doneButton.background = surface(Ink.BLACK, 50, Ink.RING)
        doneButton.setTextColor(Color.parseColor(Ink.PAPER))
        paintLight()
    }

    private fun paintLight() {
        val on = Torch.isOn()
        val ink = Color.parseColor(if (on) "#F3E2B8" else Ink.PAPER)
        lightButton.setColorFilter(ink)
        val disk = lightButton.background as GradientDrawable
        disk.setStroke(dp(1), Color.parseColor(if (on) "#F3E2B8" else Ink.RING))
        disk.setColor(if (on) Color.parseColor("#22F3E2B8") else Color.TRANSPARENT)
    }

    private fun iconButton(icon: Int, name: String, onClick: () -> Unit): ImageButton {
        val button = ImageButton(this)
        button.setImageResource(icon)
        button.contentDescription = name
        button.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        button.setPadding(dp(16), dp(16), dp(16), dp(16))
        button.setColorFilter(Color.parseColor(Ink.PAPER))
        val disk = GradientDrawable()
        disk.shape = GradientDrawable.OVAL
        disk.setColor(Color.TRANSPARENT)
        disk.setStroke(dp(1), Color.parseColor(Ink.RING))
        button.background = disk
        val params = LinearLayout.LayoutParams(dp(72), dp(72))
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
