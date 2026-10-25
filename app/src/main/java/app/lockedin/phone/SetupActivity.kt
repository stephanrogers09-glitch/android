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

class SetupActivity : Activity() {
    private lateinit var line: EditText
    private lateinit var understand: CheckBox
    private lateinit var lockButton: Button
    private lateinit var setupBox: LinearLayout
    private val choices = linkedMapOf<Preset, Button>()
    private var selected = Preset.M25

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor(COLOR_BG))
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(24), dp(56), dp(24), dp(36))
        root.addView(column)

        column.addView(TextView(this).apply {
            text = "Locked In"
            setTextColor(Color.parseColor(COLOR_FG))
            textSize = 40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        column.addView(TextView(this).apply {
            text = "Pick a time. Write one line. The rest of the phone waits."
            quiet(this@SetupActivity, 16f)
            setPadding(0, dp(12), 0, dp(28))
        })

        listOf(Preset.M25, Preset.M90, Preset.MORNING, Preset.M2).forEach { preset ->
            val button = Button(this)
            button.text = choiceLabel(preset)
            button.isAllCaps = false
            button.textSize = 18f
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

        column.addView(TextView(this).apply {
            text = "Your line"
            quiet(this@SetupActivity, 14f)
            setPadding(0, dp(18), 0, dp(6))
        })
        line = EditText(this)
        line.hint = "Do it."
        line.setHintTextColor(Color.parseColor(COLOR_MUTED))
        line.setTextColor(Color.parseColor(COLOR_FG))
        line.textSize = 22f
        line.setBackgroundColor(Color.TRANSPARENT)
        line.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        line.maxLines = 2
        line.filters = arrayOf(android.text.InputFilter.LengthFilter(80))
        column.addView(line)
        column.addView(View(this).apply {
            setBackgroundColor(Color.parseColor(COLOR_LINE))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })
        column.addView(TextView(this).apply {
            text = "Do it."
            setTextColor(Color.parseColor("#6F6A62"))
            textSize = 18f
            setPadding(0, dp(14), 0, dp(8))
            setOnClickListener {
                line.setText("Do it.")
                line.setSelection(line.text.length)
            }
        })

        setupBox = LinearLayout(this)
        setupBox.orientation = LinearLayout.VERTICAL
        setupBox.setPadding(0, dp(12), 0, 0)
        column.addView(setupBox)

        understand = CheckBox(this)
        understand.text = "Other apps stay closed until the time ends. Calls still come in."
        understand.setTextColor(Color.parseColor(COLOR_MUTED))
        understand.textSize = 15f
        understand.setPadding(0, dp(16), 0, dp(8))
        column.addView(understand)

        lockButton = Button(this)
        lockButton.text = "Lock in"
        lockButton.isAllCaps = false
        lockButton.textSize = 18f
        lockButton.setOnClickListener { startLock() }
        val lockParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lockParams.topMargin = dp(12)
        lockButton.layoutParams = lockParams
        column.addView(lockButton)

        column.addView(TextView(this).apply {
            text = "Every day, or text a friend if I quit"
            setTextColor(Color.parseColor(COLOR_FG))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(8))
            setOnClickListener { openPlus(false) }
        })

        setContentView(root)
        paint()
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
                shape.setColor(Color.parseColor(COLOR_PAPER))
                button.setTextColor(Color.parseColor(COLOR_INK))
            } else {
                shape.setColor(Color.TRANSPARENT)
                shape.setStroke(dp(1), Color.parseColor(COLOR_LINE))
                button.setTextColor(Color.parseColor(COLOR_FG))
            }
            button.background = shape
            button.text = choiceLabel(preset) + if (preset.paid && !plus) "  ·  Plus" else ""
        }
        val text = if (::line.isInitialized) line.text.toString().trim() else ""
        lockButton.text = if (selected.paid && !Pass.owns(this)) "Continue" else "Lock in"
        val fill = GradientDrawable()
        fill.cornerRadius = dp(16).toFloat()
        fill.setColor(Color.parseColor(COLOR_PAPER))
        lockButton.background = fill
        lockButton.setTextColor(Color.parseColor(COLOR_INK))
        lockButton.setPadding(dp(16), dp(18), dp(16), dp(18))

        val returning = SessionStore.setupDone(this)
        understand.visibility = if (returning) View.GONE else View.VISIBLE
        buildSetup()
    }

    private fun buildSetup() {
        setupBox.removeAllViews()
        if (!granted(Manifest.permission.CALL_PHONE)) {
            setupBox.addView(setupButton("Allow calls") {
                requestPermissions(
                    arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE),
                    11
                )
            })
        }
        if (!granted(Manifest.permission.CAMERA)) {
            setupBox.addView(setupButton("Allow camera and torch") {
                requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 12)
            })
        }
        if (!Gate.accessibilityOn(this)) {
            setupBox.addView(setupButton("Allow the lock") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }
        if (!Gate.adminOn(this)) {
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
            this.textSize = 16f
            setTextColor(Color.parseColor(COLOR_FG))
            val shape = GradientDrawable()
            shape.cornerRadius = dp(12).toFloat()
            shape.setStroke(dp(1), Color.parseColor(COLOR_LINE))
            shape.setColor(Color.TRANSPARENT)
            background = shape
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
