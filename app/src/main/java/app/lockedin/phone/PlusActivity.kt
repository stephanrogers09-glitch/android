package app.lockedin.phone

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

class PlusActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var code: EditText
    private lateinit var partner: EditText
    private lateinit var dailyButton: Button
    private var days = 31
    private var hour = 22
    private var minute = 0
    private var on = false
    private var preset = Preset.M25

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val plan = SessionStore.plan(this)
        hour = plan.hour
        minute = plan.minute
        on = plan.on
        preset = plan.preset
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor(COLOR_BG))
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(24), dp(48), dp(24), dp(36))
        root.addView(column)

        column.addView(TextView(this).apply {
            text = "Plus"
            setTextColor(Color.parseColor(COLOR_FG))
            textSize = 40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        column.addView(note("25 minutes stays free. Longer locks, a daily start, and a friend are Plus."))
        status = note("")
        column.addView(status)

        column.addView(priceButton("$2  ·  31 days", 31))
        column.addView(priceButton("$15  ·  1 year", 365))

        column.addView(note("""
Pay via MTN MoMo or Airtel Money

Send the amount to:
MTN MoMo: 0770000000
Airtel Money: 0750000000

Reference: LockedIn

Then WhatsApp or SMS the transaction ID and your number. I will reply with a code. Enter it below.
""".trimIndent()))

        column.addView(note("Enter the code you received:"))
        code = field("Code", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        column.addView(code)
        column.addView(quietButton("Use code") { useCode() })

        column.addView(TextView(this).apply {
            text = "Your controls"
            setTextColor(Color.parseColor(COLOR_FG))
            textSize = 28f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(0, dp(28), 0, dp(8))
        })
        dailyButton = quietButton(dayLabel()) { pickTime() }
        column.addView(dailyButton)
        column.addView(quietButton(if (on) "Daily lock is on" else "Turn daily lock on") {
            on = !on
            savePlan()
        }.also { it.tag = "toggle" })
        partner = field("Friend's number", InputType.TYPE_CLASS_PHONE)
        partner.setText(SessionStore.partner(this))
        column.addView(partner)
        column.addView(quietButton("Save this friend") { savePartner() })
        column.addView(quietButton("Send the iPhone steps") { sendIphone() })
        column.addView(quietButton("Back") { finish() })

        setContentView(root)
        refresh()
    }

    private fun useCode() {
        if (!Pass.redeem(this, code.text.toString())) {
            status.text = "That code is not valid."
            return
        }
        code.setText("")
        status.text = "Plus is on until ${Pass.untilLabel(this)}."
        Schedule.arm(this)
        if (intent.getBooleanExtra("lock", false)) {
            beginLock()
            return
        }
        refresh()
    }

    private fun beginLock() {
        val chosen = Preset.fromId(intent.getStringExtra("preset"))
        val text = intent.getStringExtra("line").orEmpty().ifBlank { "Do it." }
        SessionStore.markSetup(this)
        SessionStore.start(this, chosen, text)
        startActivity(Intent(this, LockActivity::class.java))
        finish()
    }

    private fun pickTime() {
        TimePickerDialog(this, { _, pickedHour, pickedMinute ->
            hour = pickedHour
            minute = pickedMinute
            dailyButton.text = dayLabel()
            if (on) savePlan()
        }, hour, minute, false).show()
    }

    private fun savePlan() {
        if (!Pass.owns(this)) {
            on = false
            status.text = "Pay or enter a code first."
            refresh()
            return
        }
        val line = intent.getStringExtra("line").orEmpty().ifBlank { SessionStore.plan(this).line }
        if (on && line.isBlank()) {
            on = false
            status.text = "Write a line on the first screen first."
            refresh()
            return
        }
        SessionStore.savePlan(this, on, hour, minute, preset, line.ifBlank { "Do it." }, SessionStore.partner(this))
        if (on && Schedule.needsExactPermission(this)) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
        if (on && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 31)
        }
        Schedule.arm(this)
        status.text = if (on) "It will start ${dayLabel()}." else "Daily lock is off."
        refresh()
    }

    private fun savePartner() {
        if (!Pass.owns(this)) {
            status.text = "Pay or enter a code first."
            return
        }
        val phone = partner.text.toString().filter { it.isDigit() || it == '+' }
        if (phone.length < 8) {
            status.text = "Enter their number."
            return
        }
        val plan = SessionStore.plan(this)
        SessionStore.savePlan(this, plan.on, plan.hour, plan.minute, plan.preset, plan.line, phone)
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 32)
            return
        }
        status.text = "Saved. They get one text if you turn a lock off early."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 32 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            status.text = "Saved. They get one text if you turn a lock off early."
        }
    }

    private fun sendIphone() {
        val text = """
            Locked In keeps the phone closed.

            iPhone will not let an app do that. Do this instead: Settings, Screen Time, Use Screen Time Passcode. Make a code and give it to someone else. Do not keep it. Then set Downtime for the hours you want.

            Calls still come through. You cannot undo it without the person who has the code.
        """.trimIndent()
        val send = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        send.putExtra("sms_body", text)
        try {
            startActivity(send)
        } catch (_: Exception) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Send"))
        }
    }

    private fun refresh() {
        if (Pass.owns(this) && status.text.isNullOrBlank()) {
            status.text = "Plus is on until ${Pass.untilLabel(this)}."
        }
        val column = status.parent as LinearLayout
        column.findViewWithTag<Button>("toggle")?.text = if (on) "Daily lock is on" else "Turn daily lock on"
        if (::dailyButton.isInitialized) dailyButton.text = dayLabel()
    }

    private fun dayLabel(): String {
        val suffix = if (hour < 12) "am" else "pm"
        val shown = if (hour % 12 == 0) 12 else hour % 12
        return "Every day at $shown:${minute.toString().padStart(2, '0')} $suffix"
    }

    private fun priceButton(label: String, length: Int): Button {
        return quietButton(label) {
            days = length
            refresh()
        }
    }

    private fun note(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            quiet(this@PlusActivity, 15f)
            setPadding(0, dp(8), 0, dp(8))
        }
    }

    private fun field(hint: String, type: Int): EditText {
        return EditText(this).apply {
            this.hint = hint
            setHintTextColor(Color.parseColor(COLOR_MUTED))
            setTextColor(Color.parseColor(COLOR_FG))
            textSize = 18f
            setBackgroundColor(Color.TRANSPARENT)
            inputType = type
            setPadding(0, dp(14), 0, dp(10))
        }
    }

    private fun solidButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 18f
            setTextColor(Color.parseColor(COLOR_INK))
            val shape = GradientDrawable()
            shape.cornerRadius = dp(16).toFloat()
            shape.setColor(Color.parseColor(COLOR_PAPER))
            background = shape
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setOnClickListener { onClick() }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(12)
            params.bottomMargin = dp(8)
            layoutParams = params
            gravity = Gravity.CENTER
        }
    }

    private fun quietButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.parseColor(COLOR_FG))
            val shape = GradientDrawable()
            shape.cornerRadius = dp(14).toFloat()
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
            gravity = Gravity.CENTER
        }
    }
}
