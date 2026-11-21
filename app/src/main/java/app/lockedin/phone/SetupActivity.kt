package app.lockedin.phone

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The Focus tab: choose a length, write the commitment, grant the lock, and start. */
class SetupActivity : Activity() {
    private lateinit var line: EditText
    private lateinit var understand: CheckBox
    private lateinit var lockButton: Button
    private lateinit var setupBox: LinearLayout
    private val choices = linkedMapOf<Preset, Button>()
    private var selected = Preset.M25

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(Ink.BLACK))
            fitsSystemWindows = true
        }
        val scroll = ScrollView(this)
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(24), dp(56), dp(24), dp(32))
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(tabBar(Tab.FOCUS))

        column.addView(TextView(this).apply {
            text = "Focus"
            setTextColor(Color.parseColor(Ink.TEXT))
            textSize = 40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        column.addView(caption("Choose a length. Write one line.", Ink.DIM).apply {
            setPadding(0, dp(8), 0, dp(28))
        })

        column.addView(caption("Length", Ink.FAINT).apply { setPadding(0, 0, 0, dp(10)) })
        listOf(Preset.M25, Preset.M90, Preset.MORNING, Preset.M2).forEach { preset ->
            val button = Button(this)
            button.text = choiceLabel(preset)
            button.isAllCaps = false
            button.textSize = 17f
            button.setOnClickListener {
                selected = preset
                paint()
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(10)
            button.layoutParams = params
            choices[preset] = button
            column.addView(button)
        }

        column.addView(caption("Commitment", Ink.FAINT).apply { setPadding(0, dp(18), 0, dp(6)) })
        line = EditText(this)
        line.hint = "Do it."
        line.setHintTextColor(Color.parseColor(Ink.FAINT))
        line.setTextColor(Color.parseColor(Ink.TEXT))
        line.textSize = 22f
        line.setBackgroundColor(Color.TRANSPARENT)
        line.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        line.maxLines = 2
        line.filters = arrayOf(android.text.InputFilter.LengthFilter(80))
        column.addView(line)
        column.addView(rule())
        column.addView(pill("Use “Do it.”").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            setOnClickListener {
                line.setText("Do it.")
                line.setSelection(line.text.length)
            }
        })

        setupBox = LinearLayout(this)
        setupBox.orientation = LinearLayout.VERTICAL
        setupBox.setPadding(0, dp(18), 0, 0)
        column.addView(setupBox)

        understand = CheckBox(this)
        understand.text = "Other apps stay closed until the time ends. Calls still come in."
        understand.setTextColor(Color.parseColor(Ink.DIM))
        understand.textSize = 14f
        understand.setPadding(0, dp(16), 0, dp(8))
        column.addView(understand)

        lockButton = Button(this)
        lockButton.text = "Lock in"
        lockButton.isAllCaps = false
        lockButton.textSize = 17f
        lockButton.setOnClickListener { startLock() }
        val lockParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lockParams.topMargin = dp(12)
        lockButton.layoutParams = lockParams
        column.addView(lockButton)

        column.addView(TextView(this).apply {
            text = "Schedule and accountability"
            setTextColor(Color.parseColor(Ink.DIM))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
            setOnClickListener { openPlus(false) }
        })

        // Opened from the study workspace: start with the note's focus line and the chosen length.
        intent?.getStringExtra(EXTRA_LINE)?.let {
            line.setText(it)
            line.setSelection(line.text.length)
        }
        intent?.getStringExtra(EXTRA_PRESET)?.let { selected = Preset.fromId(it) }
        setContentView(root)
        paint()
    }

    companion object {
        const val EXTRA_PRESET = "app.lockedin.phone.PRESET"
        const val EXTRA_LINE = "app.lockedin.phone.LINE"
    }

    override fun onResume() {
        super.onResume()
        if (SessionStore.isActive(this)) {
            startActivity(Intent(this, LockActivity::class.java))
            return
        }
        paint()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        paint()
    }

    private fun startLock() {
        val text = line.text.toString().trim().ifBlank { "Do it." }
        if (!ready()) {
            if (!SessionStore.setupDone(this) && !understand.isChecked) {
                lockButton.text = "Tick the box first"
            }
            return
        }
        if (selected.paid && !Pass.owns(this)) {
            openPlus(true)
            return
        }
        SessionStore.markSetup(this)
        SessionStore.start(this, selected, text)
        startActivity(Intent(this, LockActivity::class.java))
    }

    private fun openPlus(andLock: Boolean) {
        startActivity(Intent(this, PlusActivity::class.java).apply {
            putExtra("line", line.text.toString().trim().ifBlank { "Do it." })
            putExtra("preset", selected.id)
            putExtra("lock", andLock)
        })
    }

    private fun paint() {
        val plus = Pass.owns(this)
        choices.forEach { (preset, button) ->
            val on = preset == selected
            val shape = GradientDrawable()
            shape.cornerRadius = dp(14).toFloat()
            if (on) {
                shape.setColor(Color.parseColor(Ink.TEXT))
                button.setTextColor(Color.parseColor(Ink.BLACK))
            } else {
                shape.setColor(Color.TRANSPARENT)
                shape.setStroke(dp(1), Color.parseColor(Ink.HAIRLINE))
                button.setTextColor(Color.parseColor(Ink.TEXT))
            }
            button.background = shape
            button.setPadding(dp(18), dp(16), dp(18), dp(16))
            button.text = choiceLabel(preset) + if (preset.paid && !plus) "  ·  Plus" else ""
        }
        lockButton.text = if (selected.paid && !Pass.owns(this)) "Continue" else "Lock in"
        val fill = GradientDrawable()
        fill.cornerRadius = dp(50).toFloat()
        fill.setColor(Color.parseColor(Ink.TEXT))
        lockButton.background = fill
        lockButton.setTextColor(Color.parseColor(Ink.BLACK))
        lockButton.setPadding(dp(16), dp(16), dp(16), dp(16))

        val returning = SessionStore.setupDone(this)
        understand.visibility = if (returning) View.GONE else View.VISIBLE
        buildSetup()
    }

    private fun buildSetup() {
        setupBox.removeAllViews()
        setupBox.addView(caption("Permissions", Ink.FAINT).apply { setPadding(0, 0, 0, dp(10)) })
        var needed = false
        if (!granted(Manifest.permission.CALL_PHONE)) {
            needed = true
            setupBox.addView(setupButton("Allow calls") {
                requestPermissions(
                    arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE),
                    11
                )
            })
        }
        if (!granted(Manifest.permission.CAMERA)) {
            needed = true
            setupBox.addView(setupButton("Allow camera and torch") {
                requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 12)
            })
        }
        if (!Gate.accessibilityOn(this)) {
            needed = true
            setupBox.addView(setupButton("Allow the lock") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }
        if (!Gate.adminOn(this)) {
            needed = true
            setupBox.addView(setupButton("Block uninstall during a lock") {
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Gate.adminComponent(this@SetupActivity))
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "While a lock is running, the app cannot be uninstalled until the time ends."
                    )
                })
            })
        }
        if (!needed) {
            setupBox.addView(caption("All set", Ink.DIM).apply { setPadding(0, 0, 0, dp(4)) })
        }
    }

    private fun ready(): Boolean {
        val consent = SessionStore.setupDone(this) || understand.isChecked
        return consent &&
            Gate.accessibilityOn(this) &&
            Gate.adminOn(this) &&
            granted(Manifest.permission.CALL_PHONE) &&
            granted(Manifest.permission.CAMERA)
    }

    private fun granted(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun choiceLabel(preset: Preset): String {
        return when (preset) {
            Preset.M25 -> "25 minutes"
            Preset.M90 -> "90 minutes"
            Preset.MORNING -> "Until morning"
            Preset.M2 -> "2 minutes, to test"
            else -> preset.label
        }
    }

    private fun setupButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            this.textSize = 15f
            setTextColor(Color.parseColor(Ink.TEXT))
            val shape = GradientDrawable()
            shape.cornerRadius = dp(50).toFloat()
            shape.setStroke(dp(1), Color.parseColor(Ink.HAIRLINE))
            shape.setColor(Color.TRANSPARENT)
            background = shape
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(8)
            layoutParams = params
        }
    }
}
