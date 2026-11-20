package app.lockedin.phone

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiResult
import app.lockedin.core.intelligence.AiTask
import app.lockedin.core.intelligence.AiRules
import app.lockedin.core.intelligence.Unavailability

/**
 * Study session with the AI helper. The user picks a task, shares the study material they want
 * help with, and reads the answer. Only the text on this screen leaves the device, and only when
 * they press Send.
 */
class AiSessionActivity : Activity() {
    private lateinit var taskPicker: Spinner
    private lateinit var material: EditText
    private lateinit var question: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private val client = AiClient()

    private val tasks = AiTask.values()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this)
        root.setBackgroundColor(Color.parseColor(COLOR_BG))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }
        root.addView(page)

        page.addView(TextView(this).apply {
            text = "STUDY SESSION"
            quiet(this@AiSessionActivity, 13f, Color.parseColor(COLOR_MUTED))
            letterSpacing = 0.18f
        })

        taskPicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AiSessionActivity,
                android.R.layout.simple_spinner_dropdown_item,
                tasks.map { labelFor(it) },
            )
        }
        page.addView(taskPicker)

        material = EditText(this).apply {
            hint = "Paste the notes or material to work from"
            minLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.parseColor(COLOR_FG))
            setHintTextColor(Color.parseColor(COLOR_MUTED))
            setBackgroundColor(Color.parseColor(COLOR_LINE))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        page.addView(material.also { it.fillWidth() })

        question = EditText(this).apply {
            hint = "Your question (only for Ask)"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.parseColor(COLOR_FG))
            setHintTextColor(Color.parseColor(COLOR_MUTED))
            setBackgroundColor(Color.parseColor(COLOR_LINE))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        page.addView(question.also { it.fillWidth() })

        send = Button(this).apply {
            text = "Send"
            setOnClickListener { onSend() }
        }
        send.outline(this)
        page.addView(send.also { it.fillWidth() })

        status = TextView(this).apply { quiet(this@AiSessionActivity, 14f) }
        page.addView(status.also { it.fillWidth() })

        answer = TextView(this).apply {
            setTextColor(Color.parseColor(COLOR_FG))
            setTextSize(16f)
            setPadding(0, dp(16), 0, 0)
        }
        page.addView(answer.also { it.fillWidth() })

        setContentView(root)
    }

    private fun onSend() {
        val task = tasks[taskPicker.selectedItemPosition]
        val request = AiRequest(
            task = task,
            context = material.text.toString(),
            question = if (task == AiTask.ASK) question.text.toString() else null,
        )
        AiRules.validate(request)?.let { status.text = it; return }

        send.isEnabled = false
        send.outline(this)
        status.text = "Thinking…"
        answer.text = ""
        client.send(request) { result ->
            send.isEnabled = true
            send.outline(this)
            show(result)
        }
    }

    private fun show(result: AiResult) {
        when (result) {
            is AiResult.Success -> {
                status.text = ""
                answer.text = result.text
            }
            is AiResult.Unavailable -> status.text = unavailableText(result.reason)
            is AiResult.QuotaExceeded -> status.text = "You have used today's AI requests. They reset at midnight (Kampala time)."
            is AiResult.Rejected -> status.text = result.message
            is AiResult.Failed -> status.text = result.message
        }
    }

    private fun unavailableText(reason: Unavailability): String = when (reason) {
        Unavailability.NOT_SIGNED_IN -> "Sign-in is not set up in this build, so the AI helper is unavailable."
        Unavailability.OFFLINE -> "No connection. Your study session still works offline."
        Unavailability.NOT_CONFIGURED -> "The AI helper is not connected to a server yet."
        Unavailability.SERVICE_DOWN -> "The AI helper is switched off right now."
    }

    private fun labelFor(task: AiTask): String = when (task) {
        AiTask.EXPLAIN -> "Explain"
        AiTask.SUMMARISE -> "Summarise"
        AiTask.REVISION_QUESTIONS -> "Revision questions"
        AiTask.QUIZ -> "Quiz me"
        AiTask.STUDY_PLAN -> "Study plan"
        AiTask.ASK -> "Ask a question"
    }
}
