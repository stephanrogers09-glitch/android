package app.lockedin.phone

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiResult
import app.lockedin.core.intelligence.AiRules
import app.lockedin.core.intelligence.AiTask
import app.lockedin.core.intelligence.Unavailability

/**
 * Study session with the AI helper. The student signs in, picks a task, shares the material they
 * want help with and reads the answer. Only the text on this screen leaves the device, and only
 * when they press Send. Gemini is called by the server function, never from this device.
 */
class AiSessionActivity : Activity() {
    private lateinit var signInBlock: LinearLayout
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var signInStatus: TextView
    private lateinit var sessionBlock: LinearLayout
    private lateinit var taskPicker: Spinner
    private lateinit var material: EditText
    private lateinit var question: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var answer: TextView

    private val manager by lazy { AppSession.manager(this) }
    private val auth = AuthClient()
    private val client by lazy {
        AiClient(token = { manager.accessToken(System.currentTimeMillis()) })
    }
    private lateinit var signInButton: Button
    private val tasks = AiTask.values()
    private val main = Handler(Looper.getMainLooper())

    /** Increments on every state check, so a slow older check can never overwrite a newer one. */
    private var stateGeneration = 0

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

        // Sign-in block: shown until the student has a usable session.
        signInBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        email = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        signInBlock.addView(email.also { it.fillWidth() })
        signInBlock.addView(password.also { it.fillWidth() })
        signInButton = Button(this).apply {
            text = "Sign in"
            setOnClickListener { onSignIn() }
            outline(this@AiSessionActivity)
        }
        signInBlock.addView(signInButton.also { it.fillWidth() })
        signInStatus = TextView(this).apply { quiet(this@AiSessionActivity, 14f) }
        signInBlock.addView(signInStatus.also { it.fillWidth() })
        page.addView(signInBlock)

        // Session block: shown once signed in.
        sessionBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sessionBlock.addView(TextView(this).apply { quiet(this@AiSessionActivity, 14f); text = "Signed in." }.also { it.fillWidth() })
        sessionBlock.addView(Button(this).apply {
            text = "Sign out"
            setOnClickListener { onSignOut() }
            outline(this@AiSessionActivity)
        }.also { it.fillWidth() })

        taskPicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AiSessionActivity,
                android.R.layout.simple_spinner_dropdown_item,
                tasks.map { labelFor(it) },
            )
        }
        sessionBlock.addView(taskPicker)

        material = EditText(this).apply {
            hint = "Paste the notes or material to work from"
            minLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Color.parseColor(COLOR_FG))
            setHintTextColor(Color.parseColor(COLOR_MUTED))
            setBackgroundColor(Color.parseColor(COLOR_LINE))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        sessionBlock.addView(material.also { it.fillWidth() })

        question = EditText(this).apply {
            hint = "Your question (only for Ask a question)"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.parseColor(COLOR_FG))
            setHintTextColor(Color.parseColor(COLOR_MUTED))
            setBackgroundColor(Color.parseColor(COLOR_LINE))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        sessionBlock.addView(question.also { it.fillWidth() })

        send = Button(this).apply {
            text = "Send"
            setOnClickListener { onSend() }
            outline(this@AiSessionActivity)
        }
        sessionBlock.addView(send.also { it.fillWidth() })

        status = TextView(this).apply { quiet(this@AiSessionActivity, 14f) }
        sessionBlock.addView(status.also { it.fillWidth() })

        answer = TextView(this).apply {
            setTextColor(Color.parseColor(COLOR_FG))
            textSize = 16f
            setPadding(0, dp(16), 0, 0)
        }
        sessionBlock.addView(answer.also { it.fillWidth() })

        page.addView(sessionBlock)
        // Both blocks start hidden: the session check runs in the background and decides which one shows.
        signInBlock.visibility = View.GONE
        sessionBlock.visibility = View.GONE
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun field(hint: String, type: Int): EditText = EditText(this).apply {
        this.hint = hint
        inputType = type
        setTextColor(Color.parseColor(COLOR_FG))
        setHintTextColor(Color.parseColor(COLOR_MUTED))
        setBackgroundColor(Color.parseColor(COLOR_LINE))
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    /**
     * Decides between sign-in and the study tools. The Keystore read happens on a background thread,
     * so the screen never blocks. A missing build setting is shown without any read at all.
     */
    private fun render() {
        val setup = BackendConfig.problem()
        val generation = ++stateGeneration
        if (setup != null) {
            applyState(generation, setup = setup, signedIn = false)
            return
        }
        Thread {
            val signedIn = manager.hasSession()
            main.post { applyState(generation, setup = null, signedIn = signedIn) }
        }.start()
    }

    private fun applyState(generation: Int, setup: String?, signedIn: Boolean) {
        if (generation != stateGeneration) return
        signInBlock.visibility = if (signedIn) View.GONE else View.VISIBLE
        sessionBlock.visibility = if (signedIn) View.VISIBLE else View.GONE
        signInButton.isEnabled = setup == null
        if (setup != null) signInStatus.text = setup
    }

    private fun onSignIn() {
        signInStatus.text = "Signing in…"
        signInButton.isEnabled = false
        auth.signIn(manager, email.text.toString(), password.text.toString()) { message ->
            password.setText("")
            signInButton.isEnabled = true
            if (message == null) {
                signInStatus.text = ""
                render()
            } else {
                signInStatus.text = message
            }
        }
    }

    private fun onSignOut() {
        // Clearing the Keystore record and the server revoke both run in the background.
        // Local sign-out still happens at once from the student's point of view: the screen returns to sign-in.
        Thread {
            val token = manager.signOut()
            token?.let { auth.revoke(it) }
            main.post { render() }
        }.start()
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
        status.text = "Thinking…"
        answer.text = ""
        client.send(request) { result ->
            send.isEnabled = true
            show(result)
        }
    }

    private fun show(result: AiResult) {
        when (result) {
            is AiResult.Success -> {
                status.text = ""
                answer.text = result.text
            }
            is AiResult.Unavailable -> {
                if (result.reason == Unavailability.NOT_SIGNED_IN) {
                    // The session ended. The manager already cleared it, so return to sign-in.
                    render()
                    signInStatus.text = "Your session has ended. Sign in again."
                }
                status.text = unavailableText(result.reason)
            }
            is AiResult.QuotaExceeded -> status.text = "You have used today's AI requests. They reset at midnight (Kampala time)."
            is AiResult.Rejected -> status.text = result.message
            is AiResult.Failed -> status.text = result.message
        }
    }

    private fun unavailableText(reason: Unavailability): String = when (reason) {
        Unavailability.NOT_SIGNED_IN -> "Sign in to use the AI helper."
        Unavailability.OFFLINE -> "Could not reach the server. Your notes are still here; try again when you are online."
        Unavailability.NOT_CONFIGURED -> BackendConfig.problem() ?: "The AI helper is not set up in this build."
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
