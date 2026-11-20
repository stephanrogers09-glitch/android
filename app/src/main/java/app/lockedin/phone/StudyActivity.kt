package app.lockedin.phone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.lockedin.core.intelligence.AiRequest
import app.lockedin.core.intelligence.AiResult
import app.lockedin.core.intelligence.AiRules
import app.lockedin.core.intelligence.Unavailability
import app.lockedin.core.notes.Note
import app.lockedin.core.notes.NoteBook
import app.lockedin.core.study.StudyAction
import app.lockedin.core.study.StudyContext
import app.lockedin.core.study.StudyContextCodec
import app.lockedin.core.study.StudyFocus
import app.lockedin.core.study.StudyMaterial
import app.lockedin.core.study.StudyPrompts
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The study workspace. One screen holds the student's notes, the note they are writing, and a tutor
 * that answers from that note. Notes and focus sessions work without an account. The tutor needs a
 * signed-in student and a configured backend, and it says plainly when either is missing.
 *
 * Only the text the student selects or the note they are working on is sent, and only when they press
 * an action. Gemini is called by the server function, never from this device.
 */
class StudyActivity : Activity() {
    private lateinit var page: LinearLayout
    private lateinit var libraryBlock: LinearLayout
    private lateinit var resumeHolder: LinearLayout
    private lateinit var noteList: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var workspaceBlock: LinearLayout
    private lateinit var editor: EditText
    private lateinit var savedText: TextView
    private lateinit var workingFrom: TextView
    private lateinit var tutorNotice: TextView
    private lateinit var signInBox: LinearLayout
    private lateinit var readyBox: LinearLayout
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var signInButton: Button
    private lateinit var signInStatus: TextView
    private lateinit var question: EditText
    private lateinit var askButton: Button
    private lateinit var actionButtons: List<Button>
    private lateinit var tutorStatus: TextView
    private lateinit var answerText: TextView

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val autoSave = Runnable { saveNow() }
    private val manager by lazy { AppSession.manager(this) }
    private val auth = AuthClient()
    private val client by lazy { AiClient(token = { manager.accessToken(System.currentTimeMillis()) }) }

    private var book = NoteBook()
    private var openId: String? = null
    private var suppressSave = false
    private var tutorGeneration = 0
    private var lastAnswerNoteId: String? = null
    private var lastAnswer = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor(COLOR_BG)) }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(40))
        }
        scroll.addView(page)

        page.addView(TextView(this).apply {
            text = "STUDY"
            quiet(this@StudyActivity, 13f, Color.parseColor(COLOR_MUTED))
            letterSpacing = 0.18f
        })
        buildLibrary()
        buildWorkspace()
        setContentView(scroll)

        // The notes file and the saved workspace state are read in the background.
        io.execute {
            val loaded = NotesStore.load(applicationContext)
            val context = readContext()
            main.post {
                book = loaded
                draftFrom(context)
                showLibrary()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveNow()
        saveContext()
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(autoSave)
        io.shutdown()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (workspaceBlock.visibility == View.VISIBLE) closeNote() else super.onBackPressed()
    }

    // --- Library -----------------------------------------------------------------------------

    private fun buildLibrary() {
        libraryBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resumeHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryBlock.addView(resumeHolder.also { it.fillWidth() })
        libraryBlock.addView(actionButton("New note") { newNote() }.also { it.fillWidth() })
        emptyText = TextView(this).apply {
            text = "No notes yet. Write one, then ask the tutor about it."
            quiet(this@StudyActivity, 15f)
            setPadding(0, dp(14), 0, dp(8))
        }
        libraryBlock.addView(emptyText.also { it.fillWidth() })
        noteList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryBlock.addView(noteList.also { it.fillWidth() })
        page.addView(libraryBlock)
    }

    private fun showLibrary() {
        openId = null
        workspaceBlock.visibility = View.GONE
        libraryBlock.visibility = View.VISIBLE
        resumeHolder.removeAllViews()
        noteList.removeAllViews()

        book.lastOpened()?.let { last ->
            resumeHolder.addView(actionButton("Continue: ${last.title}") { openNote(last.id) }.also { it.fillWidth() })
        }
        val notes = book.sorted()
        emptyText.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        for (note in notes) {
            noteList.addView(actionButton(note.title) { openNote(note.id) }.also { it.fillWidth() })
        }
    }

    private fun newNote() {
        val id = UUID.randomUUID().toString()
        val next = book.upsert(Note(id, "", System.currentTimeMillis()))
        if (next.find(id) == null) {
            Toast.makeText(this, "The notebook is full. Delete a note to add another.", Toast.LENGTH_LONG).show()
            return
        }
        book = next
        openNote(id)
    }

    // --- Workspace ---------------------------------------------------------------------------

    private fun buildWorkspace() {
        workspaceBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(actionButton("Notes") { closeNote() }, weightedParams())
        top.addView(actionButton("Start focus") { startFocus() }, weightedParams())
        top.addView(actionButton("Delete") { deleteOpen() }, weightedParams())
        workspaceBlock.addView(top.also { it.fillWidth() })

        savedText = TextView(this).apply { quiet(this@StudyActivity, 13f) }
        workspaceBlock.addView(savedText.also { it.fillWidth() })

        editor = EditText(this).apply {
            hint = "Write your notes here"
            textSize = 18f
            minLines = 10
            gravity = Gravity.TOP
            setTextColor(Color.parseColor(COLOR_FG))
            setHintTextColor(Color.parseColor(COLOR_MUTED))
            setBackgroundColor(Color.parseColor(COLOR_LINE))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppressSave || openId == null) return
                    savedText.text = "Saving…"
                    main.removeCallbacks(autoSave)
                    main.postDelayed(autoSave, AUTO_SAVE_DELAY_MS)
                }
            })
        }
        workspaceBlock.addView(editor.also { it.fillWidth() })

        workingFrom = TextView(this).apply {
            quiet(this@StudyActivity, 13f)
            setPadding(0, dp(6), 0, 0)
        }
        workspaceBlock.addView(workingFrom.also { it.fillWidth() })

        // The tutor card. Its content is chosen by tutorState(): notice, sign-in, or the tools.
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor(COLOR_LINE))
                setColor(Color.parseColor(COLOR_CARD))
            }
        }
        card.addView(TextView(this).apply {
            text = "TUTOR"
            quiet(this@StudyActivity, 12f, Color.parseColor(COLOR_MUTED))
            letterSpacing = 0.16f
        })
        tutorNotice = TextView(this).apply {
            quiet(this@StudyActivity, 14f)
            setPadding(0, dp(8), 0, dp(8))
        }
        card.addView(tutorNotice.also { it.fillWidth() })

        signInBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        email = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        signInBox.addView(email.also { it.fillWidth() })
        signInBox.addView(password.also { it.fillWidth() })
        signInButton = actionButton("Sign in") { onSignIn() }
        signInBox.addView(signInButton.also { it.fillWidth() })
        signInStatus = TextView(this).apply { quiet(this@StudyActivity, 14f) }
        signInBox.addView(signInStatus.also { it.fillWidth() })
        card.addView(signInBox.also { it.fillWidth() })

        readyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val buttons = listOf(
            "Explain" to StudyAction.EXPLAIN,
            "Summarise" to StudyAction.SUMMARISE,
            "Quiz me" to StudyAction.QUIZ,
            "Test my understanding" to StudyAction.CHECK,
            "Give examples" to StudyAction.EXAMPLES,
        )
        val made = buttons.map { (label, action) -> actionButton(label) { runAction(action) } }
        actionButtons = made
        readyBox.addView(row(made[0], made[1], made[2]).also { it.fillWidth() })
        readyBox.addView(row(made[3], made[4]).also { it.fillWidth() })

        question = EditText(this).apply {
            hint = "Ask about this note…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 16f
            setTextColor(Color.parseColor(COLOR_FG))
            setHintTextColor(Color.parseColor(COLOR_MUTED))
            setBackgroundColor(Color.parseColor(COLOR_LINE))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        readyBox.addView(question.also { it.fillWidth() })
        askButton = actionButton("Ask") { askQuestion() }
        readyBox.addView(askButton.also { it.fillWidth() })
        card.addView(readyBox.also { it.fillWidth() })

        tutorStatus = TextView(this).apply { quiet(this@StudyActivity, 14f) }
        card.addView(tutorStatus.also { it.fillWidth() })
        answerText = TextView(this).apply {
            setTextColor(Color.parseColor(COLOR_FG))
            textSize = 16f
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        }
        card.addView(answerText.also { it.fillWidth() })

        workspaceBlock.addView(card.also {
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = dp(16)
            it.layoutParams = params
        })

        workspaceBlock.visibility = View.GONE
        page.addView(workspaceBlock)
    }

    private fun openNote(id: String) {
        val note = book.find(id) ?: return
        openId = id
        book = book.opened(id)
        suppressSave = true
        editor.setText(note.body)
        suppressSave = false
        savedText.text = ""
        answerText.text = if (lastAnswerNoteId == id) lastAnswer else ""
        tutorStatus.text = ""
        libraryBlock.visibility = View.GONE
        workspaceBlock.visibility = View.VISIBLE
        updateWorkingFrom()
        refreshTutor()
        persist()
    }

    private fun closeNote() {
        saveNow()
        saveContext()
        showLibrary()
    }

    private fun saveNow() {
        val id = openId ?: return
        main.removeCallbacks(autoSave)
        val existing = book.find(id) ?: return
        val body = editor.text.toString()
        if (body == existing.body) {
            savedText.text = ""
            return
        }
        book = book.upsert(Note(id, body, System.currentTimeMillis()))
        savedText.text = "Saved"
        updateWorkingFrom()
        persist()
    }

    private fun deleteOpen() {
        val id = openId ?: return
        main.removeCallbacks(autoSave)
        book = book.remove(id)
        persist()
        showLibrary()
    }

    private fun startFocus() {
        saveNow()
        val note = openId?.let { book.find(it) } ?: return
        startActivity(
            Intent(this, SetupActivity::class.java)
                .putExtra(SetupActivity.EXTRA_PRESET, Preset.M25.id)
                .putExtra(SetupActivity.EXTRA_LINE, StudyFocus.lineFor(note.title)),
        )
    }

    private fun updateWorkingFrom() {
        val selection = selectedText()
        workingFrom.text = if (selection.isNotEmpty()) {
            "Working from your selection (${selection.length} characters). Clear the selection to use the whole note."
        } else {
            "Working from the whole note. Select text to focus the tutor on part of it."
        }
    }

    // --- Tutor -------------------------------------------------------------------------------

    private enum class TutorState { NOT_CONFIGURED, SIGN_IN, READY }

    /** Decides what the tutor card shows. The sign-in check reads the Keystore off the main thread. */
    private fun refreshTutor() {
        val problem = BackendConfig.problem()
        if (problem != null) {
            showTutor(TutorState.NOT_CONFIGURED)
            tutorNotice.text = "The tutor is waiting for backend setup in this build, so it cannot answer yet. " +
                "Notes and focus sessions work without it. ($problem)"
            return
        }
        val generation = ++tutorGeneration
        io.execute {
            val signedIn = manager.hasSession()
            main.post {
                if (generation == tutorGeneration) {
                    showTutor(if (signedIn) TutorState.READY else TutorState.SIGN_IN)
                }
            }
        }
    }

    private fun showTutor(state: TutorState) {
        signInBox.visibility = if (state == TutorState.SIGN_IN) View.VISIBLE else View.GONE
        readyBox.visibility = if (state == TutorState.READY) View.VISIBLE else View.GONE
        tutorNotice.visibility = if (state == TutorState.READY) View.GONE else View.VISIBLE
        if (state == TutorState.SIGN_IN) {
            tutorNotice.text = "Sign in to use the tutor. Your notes stay on this phone."
        }
    }

    private fun runAction(action: StudyAction) {
        send(StudyPrompts.requestFor(action, currentMaterial()))
    }

    private fun askQuestion() {
        send(StudyPrompts.questionRequest(currentMaterial(), question.text.toString()))
    }

    private fun send(request: AiRequest) {
        AiRules.validate(request)?.let {
            tutorStatus.text = it
            return
        }
        val noteId = openId
        setBusy(true)
        tutorStatus.text = "Thinking…"
        client.send(request) { result ->
            setBusy(false)
            show(result, noteId)
        }
    }

    private fun setBusy(busy: Boolean) {
        actionButtons.forEach { it.isEnabled = !busy }
        askButton.isEnabled = !busy
    }

    private fun show(result: AiResult, noteId: String?) {
        when (result) {
            is AiResult.Success -> {
                tutorStatus.text = ""
                lastAnswerNoteId = noteId
                lastAnswer = result.text
                // An answer for a note the student has since left is kept, but not shown on screen.
                if (noteId == openId) answerText.text = result.text
                saveContext()
            }
            is AiResult.Unavailable -> {
                if (result.reason == Unavailability.NOT_SIGNED_IN) {
                    showTutor(TutorState.SIGN_IN)
                    tutorNotice.text = "Your session has ended. Sign in again to use the tutor."
                }
                tutorStatus.text = unavailableText(result.reason)
            }
            is AiResult.QuotaExceeded -> tutorStatus.text =
                "You have used today's AI requests. They reset at midnight (Kampala time)."
            is AiResult.Rejected -> tutorStatus.text = result.message
            is AiResult.Failed -> tutorStatus.text = result.message
        }
    }

    private fun unavailableText(reason: Unavailability): String = when (reason) {
        Unavailability.NOT_SIGNED_IN -> "Sign in to use the tutor."
        Unavailability.OFFLINE -> "Could not reach the server. Your notes are still here; try again when you are online."
        Unavailability.NOT_CONFIGURED -> "The tutor is not set up in this build yet."
        Unavailability.SERVICE_DOWN -> "The tutor is switched off right now."
    }

    private fun onSignIn() {
        signInStatus.text = "Signing in…"
        signInButton.isEnabled = false
        auth.signIn(manager, email.text.toString(), password.text.toString()) { message ->
            password.setText("")
            signInButton.isEnabled = true
            if (message == null) {
                signInStatus.text = ""
                refreshTutor()
            } else {
                signInStatus.text = message
            }
        }
    }

    // --- Continuity --------------------------------------------------------------------------

    private fun currentMaterial(): String = StudyMaterial.choose(editor.text.toString(), selectedText())

    private fun selectedText(): String {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        return if (start >= 0 && end > start) editor.text.subSequence(start, end).toString() else ""
    }

    /** Writes the notes snapshot on the background thread. Writes run in the order they were queued. */
    private fun persist() {
        val snapshot = book
        io.execute { NotesStore.save(applicationContext, snapshot) }
    }

    private fun draftFrom(context: StudyContext) {
        question.setText(context.draftQuestion)
        lastAnswerNoteId = context.answerNoteId
        lastAnswer = context.answer
    }

    private fun currentContext(): StudyContext = StudyContext(
        noteId = openId,
        draftQuestion = question.text.toString(),
        answerNoteId = lastAnswerNoteId,
        answer = lastAnswer,
    )

    private fun saveContext() {
        val snapshot = currentContext()
        io.execute {
            prefs().edit().putString(CONTEXT_KEY, StudyContextCodec.encode(snapshot)).apply()
        }
    }

    private fun readContext(): StudyContext =
        StudyContextCodec.decode(prefs().getString(CONTEXT_KEY, null))

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Helpers -----------------------------------------------------------------------------

    private fun field(hint: String, type: Int): EditText = EditText(this).apply {
        this.hint = hint
        inputType = type
        setTextColor(Color.parseColor(COLOR_FG))
        setHintTextColor(Color.parseColor(COLOR_MUTED))
        setBackgroundColor(Color.parseColor(COLOR_LINE))
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setOnClickListener { onClick() }
        outline(this@StudyActivity)
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (view in views) addView(view, weightedParams())
    }

    private fun weightedParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }

    private companion object {
        const val AUTO_SAVE_DELAY_MS = 700L
        const val PREFS = "study-workspace"
        const val CONTEXT_KEY = "context"
    }
}
