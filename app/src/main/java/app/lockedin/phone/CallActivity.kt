package app.lockedin.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

class CallActivity : Activity() {
    private lateinit var number: TextView
    private var dial = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessionStore.isActive(this)) {
            finish()
            return
        }
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setBackgroundColor(Color.parseColor(COLOR_BG))
        column.setPadding(dp(24), dp(24), dp(24), dp(24))

        val back = Button(this)
        back.text = "Lock"
        back.isAllCaps = false
        back.setBackgroundColor(Color.TRANSPARENT)
        back.setTextColor(Color.parseColor(COLOR_MUTED))
        back.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        back.setOnClickListener { finish() }
        column.addView(back)

        number = TextView(this)
        number.text = "—"
        number.gravity = Gravity.CENTER
        number.textSize = 32f
        number.setTextColor(Color.parseColor(COLOR_FG))
        number.setPadding(0, dp(24), 0, dp(12))
        column.addView(number)

        val grid = GridLayout(this)
        grid.columnCount = 3
        grid.useDefaultMargins = false
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del")
        keys.forEach { key ->
            val cell = Button(this)
            cell.text = if (key == "del") "Delete" else key
            cell.textSize = if (key == "del") 14f else 28f
            cell.isAllCaps = false
            cell.setBackgroundColor(Color.TRANSPARENT)
            cell.setTextColor(Color.parseColor(if (key == "del") COLOR_MUTED else COLOR_FG))
            cell.setOnClickListener {
                when (key) {
                    "" -> {}
                    "del" -> if (dial.isNotEmpty()) dial = dial.dropLast(1)
                    else -> if (dial.length < 15) dial += key
                }
                number.text = dial.ifEmpty { "—" }
            }
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.height = dp(72)
            cell.layoutParams = params
            grid.addView(cell)
        }
        column.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val spacer = android.view.View(this)
        column.addView(spacer, LinearLayout.LayoutParams(0, 0, 1f))

        val note = TextView(this)
        note.text = "Emergency calls always work"
        note.gravity = Gravity.CENTER
        note.setTextColor(Color.parseColor(COLOR_MUTED))
        note.textSize = 12f
        column.addView(note)

        val emergency = LinearLayout(this)
        emergency.gravity = Gravity.CENTER
        listOf("911", "112", "999").forEach { value ->
            val link = Button(this)
            link.text = value
            link.isAllCaps = false
            link.setBackgroundColor(Color.TRANSPARENT)
            link.setTextColor(Color.parseColor(COLOR_MUTED))
            link.setOnClickListener { place(value) }
            emergency.addView(link)
        }
        column.addView(emergency)

        val place = Button(this)
        place.text = "Call"
        place.isAllCaps = true
        place.letterSpacing = 0.18f
        place.outline(this)
        place.fillWidth()
        place.setOnClickListener { if (dial.isNotEmpty()) place(dial) }
        column.addView(place)

        setContentView(column)
    }

    private fun place(value: String) {
        try {
            Phone.place(this, value)
        } catch (_: Exception) {
        }
    }

    @Deprecated("Return to the lock, do not end it.")
    override fun onBackPressed() {
        finish()
    }
}
