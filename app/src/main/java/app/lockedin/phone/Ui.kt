package app.lockedin.phone

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

const val COLOR_BG = "#0A0A0A"
const val COLOR_FG = "#ECE8E1"
const val COLOR_MUTED = "#8E897F"
const val COLOR_LINE = "#2C2C2C"
const val COLOR_PAPER = "#F4F1EA"
const val COLOR_INK = "#14120E"

fun Context.dp(value: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()
}

fun Context.sp(value: Float): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )
}

fun View.fillWidth() {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}

fun TextView.quiet(context: Context, size: Float, color: Int = Color.parseColor(COLOR_MUTED)) {
    setTextColor(color)
    setTextSize(TypedValue.COMPLEX_UNIT_PX, context.sp(size))
    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
}

fun Button.outline(context: Context, enabledColor: Int = Color.parseColor(COLOR_FG)) {
    val drawable = GradientDrawable()
    drawable.setColor(Color.TRANSPARENT)
    drawable.setStroke(context.dp(1), if (isEnabled) enabledColor else Color.parseColor(COLOR_LINE))
    background = drawable
    setTextColor(if (isEnabled) enabledColor else Color.parseColor(COLOR_MUTED))
    setTextSize(TypedValue.COMPLEX_UNIT_PX, context.sp(14f))
    isAllCaps = true
    letterSpacing = 0.18f
    setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
    gravity = Gravity.CENTER
}
