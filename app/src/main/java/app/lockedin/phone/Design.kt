package app.lockedin.phone

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The study visual language: pure black, white type, hairline rules, pill controls, and a monospace
 * "system" voice for labels and metadata. One restrained glow marks the live element.
 */
object Ink {
    const val BLACK = "#000000"
    const val RAISED = "#111114"
    const val HAIRLINE = "#222228"
    const val TEXT = "#F4F4F5"
    const val DIM = "#8C8C96"
    const val FAINT = "#4B4B55"
    const val GLOW = "#B9D4FF"
}

/** A 1 dp rule across the full width. */
fun Context.rule(color: String = Ink.HAIRLINE): View = View(this).apply {
    setBackgroundColor(Color.parseColor(color))
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
}

/** A rounded shape with an optional hairline stroke. */
fun Context.surface(fill: String, radiusDp: Int, stroke: String? = null): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(Color.parseColor(fill))
        if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
    }

/** Small uppercase label in the monospace face. */
fun Context.caption(text: String, color: String = Ink.DIM): TextView = TextView(this).apply {
    this.text = text.uppercase()
    setTextColor(Color.parseColor(color))
    textSize = 11f
    typeface = Typeface.MONOSPACE
    letterSpacing = 0.14f
}

/** Reading text in the light sans face. */
fun Context.prose(text: String, size: Float = 16f, color: String = Ink.TEXT): TextView = TextView(this).apply {
    this.text = text
    setTextColor(Color.parseColor(color))
    textSize = size
    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    setLineSpacing(0f, 1.35f)
}

/** A pill control. Filled pills are the primary action; outlined pills are everything else. */
fun Context.pill(label: String, filled: Boolean = false): TextView = TextView(this).apply {
    text = label
    textSize = 13f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    gravity = Gravity.CENTER
    setPadding(dp(16), dp(9), dp(16), dp(9))
    if (filled) {
        background = surface(Ink.TEXT, 50)
        setTextColor(Color.parseColor(Ink.BLACK))
    } else {
        background = surface(Ink.BLACK, 50, Ink.HAIRLINE)
        setTextColor(Color.parseColor(Ink.TEXT))
    }
    isClickable = true
}
