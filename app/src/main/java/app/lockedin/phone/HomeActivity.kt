package app.lockedin.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lockedin.core.focus.FocusClock
import app.lockedin.core.study.StudyTime

/**
 * The launcher. Shows only real state: the next scheduled session, the last note, and whether the lock
 * is ready. A session that is running goes straight to the lock screen.
 */
class HomeActivity : Activity() {
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(Ink.BLACK))
            fitsSystemWindows = true
        }
        val scroll = ScrollView(this)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(32))
        }
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(tabBar(Tab.HOME))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (SessionStore.isActive(this)) {
            startActivity(Intent(this, LockActivity::class.java))
            return
        }
        paint()
    }

    private fun paint() {
        column.removeAllViews()
        column.addView(TextView(this).apply {
            text = "Locked In"
            setTextColor(Color.parseColor(Ink.TEXT))
            textSize = 40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        column.addView(caption("Focus and study", Ink.DIM).apply { setPadding(0, dp(8), 0, dp(32)) })

        column.addView(focusCard())
        column.addView(View(this), spacer(14))
        column.addView(studyCard())

        if (!Gate.accessibilityOn(this) || !Gate.adminOn(this)) {
            column.addView(View(this), spacer(14))
            column.addView(setupNotice())
        }
    }

    private fun focusCard(): LinearLayout {
        val plan = SessionStore.plan(this)
        return card("Focus").apply {
            addView(prose(if (plan.on) "Scheduled at ${clock(plan.hour, plan.minute)}" else "No schedule", 20f).apply {
                setPadding(0, dp(12), 0, 0)
            })
            addView(caption(
                if (plan.on) "${FocusClock.durationText(plan.preset.minutes)}" else "Start one now or schedule it",
                Ink.FAINT
            ).apply { setPadding(0, dp(6), 0, 0) })
            addView(pill("Open focus", filled = true).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(18) }
                setOnClickListener { openTab(Tab.FOCUS) }
            })
        }.also { it.setOnClickListener { openTab(Tab.FOCUS) } }
    }

    private fun studyCard(): LinearLayout {
        val note = NotesStore.load(this).lastOpened()
        return card("Study").apply {
            addView(prose(note?.title ?: "No notes yet", 20f).apply { setPadding(0, dp(12), 0, 0) })
            addView(caption(
                if (note != null) StudyTime.ago(System.currentTimeMillis(), note.updatedAtMs) else "Write a note and ask the tutor",
                Ink.FAINT
            ).apply { setPadding(0, dp(6), 0, 0) })
            addView(pill("Open study", filled = false).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(18) }
                setOnClickListener { openTab(Tab.STUDY) }
            })
        }.also { it.setOnClickListener { openTab(Tab.STUDY) } }
    }

    private fun setupNotice(): LinearLayout = card("Setup").apply {
        addView(prose("Finish setup before a focus session", 16f).apply { setPadding(0, dp(10), 0, 0) })
        addView(caption("Lock permission and uninstall protection are needed", Ink.FAINT).apply {
            setPadding(0, dp(6), 0, 0)
        })
        setOnClickListener { openTab(Tab.FOCUS) }
    }

    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = surface(Ink.BLACK, 18, Ink.HAIRLINE)
        setPadding(dp(20), dp(18), dp(20), dp(20))
        isClickable = true
        addView(caption(title, Ink.DIM))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun spacer(height: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height))

    private fun clock(hour: Int, minute: Int): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour < 12) "am" else "pm"
        return "$h12:${minute.toString().padStart(2, '0')} $suffix"
    }
}
