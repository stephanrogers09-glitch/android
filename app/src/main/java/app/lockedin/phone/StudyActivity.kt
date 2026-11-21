package app.lockedin.phone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
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
import app.lockedin.core.notes.NoteRules
import app.lockedin.core.study.StudyAction
import app.lockedin.core.study.StudyContext
import app.lockedin.core.study.StudyContextCodec
import app.lockedin.core.study.ClozePractice
import app.lockedin.core.study.KeyTerms
import app.lockedin.core.study.StudyFocus
import app.lockedin.core.study.StudyMaterial
import app.lockedin.core.study.StudyPrompts
import app.lockedin.core.study.StudyTime
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The study workspace. The library lists notes; opening one shows the editor with a tutor docked
 * beneath it. Notes and focus work without an account. The tutor needs a signed-in student and a
 * configured backend, and it says plainly when either is missing.
 *
 * Only the selected text, or the whole note when nothing is selected, is sent, and only when the
 * student presses an action. Gemini is called by the server function, never from this device.
 */
class StudyActivity : Activity() {
    // Library
    private lateinit var library: ScrollView
    private lateinit var libraryTabs: LinearLayout
    private lateinit var metaText: TextView
    private lateinit var sortRow: LinearLayout
    private lateinit var resumeHolder: LinearLayout
    private lateinit var noteList: LinearLayout
    private lateinit var emptyText: TextView

    // Workspace
    private lateinit var workspace: LinearLayout
    private lateinit var editor: EditText
    private lateinit var savedText: TextView
    private lateinit var wordsText: TextView
    private lateinit var contextTag: TextView
    private lateinit var tutorStatus: TextView
    private lateinit var tutorNotice: TextView
    private lateinit var signInBox: LinearLayout
    private lateinit var readyBox: LinearLayout
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var signInButton: TextView
    private lateinit var signInStatus: TextView
    private lateinit var answerScroll: ScrollView
    private lateinit var answerText: TextView
    private lateinit var question: EditText
    private lateinit var sendButton: TextView
    private lateinit var actionPills: List<TextView>

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val autoSave = Runnable { saveNow() }
    private val manager by lazy { AppSession.manager(this) }
    private val auth = AuthClient()
    private val client by lazy { AiClient(token = { manager.accessToken(System.currentTimeMillis()) }) }

    private var book = NoteBook()
    private var openId: String? = null
    private var sortByTitle = false
    private var suppressSave = false
    private var tutorGeneration = 0
    private var libraryReady = false
    private var resumeNoteId: String? = null
    private var practice: List<ClozePractice.Question> = emptyList()
    private var practiceIndex = 0
    private var practiceCorrect = 0
    private var practiceAwaitingNext = false
    private var practising = false
    private val practiceMissed = ArrayList<ClozePractice.Question>()
    private val focusControls = ArrayList<View>()
    private var lastAnswerNoteId: String? = null
    private var lastAnswer = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(Ink.BLACK))
            fitsSystemWindows = true
        }
        buildLibrary(root)
        buildWorkspace(root)
        setContentView(root)
        resumeNoteId = intent?.getStringExtra(StudyFocusLog.EXTRA_NOTE_ID)

        // The notes file and the saved workspace state are read in the background.
        io.execute {
            val loaded = NotesStore.load(applicationContext)
            val context = readContext()
            main.post {
                book = loaded
                draftFrom(context)
                libraryReady = true
                openResumed()
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
        if (workspace.visibility == View.VISIBLE) closeNote() else super.onBackPressed()
    }

    // --- Library -----------------------------------------------------------------------------

    private fun buildLibrary(root: LinearLayout) {
        library = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }
        library.addView(column)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(caption("Study", Ink.TEXT).apply { letterSpacing = 0.4f }, spacer())
        header.addView(pill("New note", filled = true).apply { setOnClickListener { newNote() } })
        column.addView(header.also { it.fillWidth() })

        column.addView(prose("Notes", 40f).apply {
            setPadding(0, dp(36), 0, dp(4))
            letterSpacing = -0.02f
        })
        metaText = caption("")
        column.addView(metaText.apply { setPadding(0, 0, 0, dp(24)) })

        sortRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        column.addView(sortRow.also { it.fillWidth() })

        resumeHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(20), 0, 0) }
        column.addView(resumeHolder.also { it.fillWidth() })

        emptyText = prose("No notes yet. Write one thought. The tutor can help with it later.", 16f, Ink.DIM).apply {
            setPadding(0, dp(28), 0, 0)
        }
        column.addView(emptyText.also { it.fillWidth() })
        noteList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(noteList.also { it.fillWidth() })

        root.addView(library, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        libraryTabs = tabBar(Tab.STUDY)
        root.addView(libraryTabs)
    }

    override fun onResume() {
        super.onResume()
        // A focus session started from here may have finished while the app was away.
        if (libraryReady && openId == null) showLibrary()
        updateFocusControls()
    }

    // Single-task: the lock screen's route comes back to this instance with a note id.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!libraryReady) return
        val id = intent.getStringExtra(StudyFocusLog.EXTRA_NOTE_ID)?.takeIf { book.find(it) != null }
        if (id != null) {
            if (openId != null) saveNow()
            openNote(id)
        } else if (openId == null) {
            showLibrary()
        }
    }

    /** Opens the note a focus session was started from, or the library. */
    private fun openResumed() {
        val id = resumeNoteId?.takeIf { book.find(it) != null }
        resumeNoteId = null
        if (id != null) openNote(id) else showLibrary()
    }

    /** Focus controls are hidden while a session runs. */
    private fun updateFocusControls() {
        val running = SessionStore.isActive(this)
        focusControls.forEach { it.visibility = if (running) View.GONE else View.VISIBLE }
    }

    private fun showLibrary() {
        StudyFocusLog.settle(this)
        openId = null
        workspace.visibility = View.GONE
        library.visibility = View.VISIBLE
        libraryTabs.visibility = View.VISIBLE
        val now = System.currentTimeMillis()
        val notes = if (sortByTitle) book.sortedByTitle() else book.sorted()

        metaText.text = "${notes.size} notes · ${notes.sumOf { NoteRules.wordCount(it.body) }} words"

        sortRow.removeAllViews()
        sortRow.addView(pill("Recent", filled = !sortByTitle).apply {
            setOnClickListener { sortByTitle = false; showLibrary() }
        })
        sortRow.addView(pill("A–Z", filled = sortByTitle).apply {
            setOnClickListener { sortByTitle = true; showLibrary() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8)
        })

        resumeHolder.removeAllViews()
        book.lastOpened()?.let { last ->
            resumeHolder.addView(resumeCard(last, now))
        }

        emptyText.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        noteList.removeAllViews()
        for (note in notes) noteList.addView(noteRow(note, now))
    }

    private fun resumeCard(note: Note, now: Long): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = surface(Ink.RAISED, 18, Ink.HAIRLINE)
        addView(caption("Continue", Ink.GLOW))
        addView(prose(note.title, 20f).apply { setPadding(0, dp(8), 0, dp(6)) })
        addView(caption(meta(note, now)))
        setOnClickListener { openNote(note.id) }
    }

    private fun noteRow(note: Note, now: Long): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(18), 0, dp(18))
        addView(prose(note.title, 17f))
        addView(caption(meta(note, now)).apply { setPadding(0, dp(6), 0, 0) })
        addView(rule())
        isClickable = true
        setOnClickListener { openNote(note.id) }
    }

    private fun meta(note: Note, now: Long): String {
        val base = "${StudyTime.ago(now, note.updatedAtMs)} · ${NoteRules.wordCount(note.body)} words"
        val focused = StudyFocusLog.minutes(this, note.id)
        return if (focused > 0) "$base · $focused min focused" else base
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

    private fun buildWorkspace(root: LinearLayout) {
        workspace = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        top.addView(pill("‹  Notes").apply { setOnClickListener { closeNote() } })
        top.addView(View(this), spacer())
        val twoMinute = pill("2 min test").apply { setOnClickListener { startFocus(Preset.M2, autostart = true) } }
        val gap = View(this)
        val twentyFive = pill("Focus · 25 min").apply { setOnClickListener { startFocus(Preset.M25, autostart = false) } }
        focusControls.addAll(listOf(twoMinute, gap, twentyFive))
        top.addView(twoMinute)
        top.addView(gap, LinearLayout.LayoutParams(dp(8), 0))
        top.addView(twentyFive)
        workspace.addView(top.also { it.fillWidth() })
        workspace.addView(rule())

        val editorScroll = ScrollView(this)
        val editorColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(28))
        }
        editor = EditText(this).apply {
            hint = "Start writing"
            setHintTextColor(Color.parseColor(Ink.FAINT))
            setTextColor(Color.parseColor(Ink.TEXT))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 18f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setLineSpacing(0f, 1.45f)
            gravity = Gravity.TOP or Gravity.START
            setPadding(0, 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppressSave || openId == null) return
                    savedText.text = "SAVING…"
                    wordsText.text = "${NoteRules.wordCount(s.toString())} WORDS"
                    main.removeCallbacks(autoSave)
                    main.postDelayed(autoSave, AUTO_SAVE_DELAY_MS)
                }
            })
        }
        editorColumn.addView(editor.also { it.fillWidth() })

        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        savedText = caption("", Ink.FAINT)
        statusRow.addView(savedText, spacer())
        wordsText = caption("", Ink.FAINT)
        statusRow.addView(wordsText)
        statusRow.addView(caption("Delete", Ink.DIM).apply {
            setPadding(dp(14), 0, 0, 0)
            isClickable = true
            setOnClickListener { deleteOpen() }
        })
        editorColumn.addView(statusRow.also { it.fillWidth() }.apply { setPadding(0, dp(18), 0, 0) })
        editorScroll.addView(editorColumn)
        workspace.addView(editorScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        workspace.addView(buildTutorPanel())
        workspace.visibility = View.GONE
        root.addView(workspace, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** The tutor sits docked under the note. It shows exactly one of: notice, sign-in, or tools. */
    private fun buildTutorPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(16))
            setBackgroundColor(Color.parseColor(Ink.BLACK))
        }
        panel.addView(rule())

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(caption("Tutor", Ink.TEXT), spacer())
        tutorStatus = caption("", Ink.DIM)
        header.addView(tutorStatus)
        panel.addView(header.also { it.fillWidth() }.apply { setPadding(0, dp(6), 0, dp(8)) })

        tutorNotice = prose("", 14f, Ink.DIM).apply { setPadding(0, 0, 0, dp(8)) }
        panel.addView(tutorNotice.also { it.fillWidth() })

        signInBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        email = underlined("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        password = underlined("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        signInBox.addView(email.also { it.fillWidth() })
        signInBox.addView(rule())
        signInBox.addView(password.also { it.fillWidth() }.apply { setPadding(0, dp(12), 0, dp(12)) })
        signInBox.addView(rule())
        signInButton = pill("Sign in", filled = true).apply {
            setOnClickListener { onSignIn() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) }
        }
        signInBox.addView(signInButton)
        signInStatus = caption("", Ink.DIM).apply { setPadding(0, dp(8), 0, 0) }
        signInBox.addView(signInStatus.also { it.fillWidth() })
        panel.addView(signInBox.also { it.fillWidth() })

        answerScroll = ScrollView(this).apply { visibility = View.GONE }
        answerText = prose("", 16f, Ink.TEXT).apply {
            setTextIsSelectable(true)
            setPadding(0, dp(4), 0, dp(12))
        }
        answerScroll.addView(answerText)
        panel.addView(answerScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)))

        readyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chips = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val actions = listOf(
            "Explain" to StudyAction.EXPLAIN,
            "Summarise" to StudyAction.SUMMARISE,
            "Quiz me" to StudyAction.QUIZ,
            "Test my understanding" to StudyAction.CHECK,
            "Give examples" to StudyAction.EXAMPLES,
        )
        actionPills = actions.map { (label, action) ->
            pill(label).apply {
                setOnClickListener { runAction(action) }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { rightMargin = dp(8) }
            }
        }
        val practise = pill("Practise").apply {
            setOnClickListener { startPractice() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { rightMargin = dp(8) }
        }
        val keyTerms = pill("Key terms").apply {
            setOnClickListener { showKeyTerms() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { rightMargin = dp(8) }
        }
        actionPills = actionPills + practise + keyTerms
        actionPills.forEach { chipRow.addView(it) }
        chips.addView(chipRow)
        readyBox.addView(chips.also { it.fillWidth() }.apply { setPadding(0, 0, 0, dp(12)) })

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(6), dp(4))
            background = surface(Ink.RAISED, 30, Ink.HAIRLINE)
        }
        contextTag = caption("Note", Ink.GLOW).apply { setPadding(0, 0, dp(10), 0) }
        inputBar.addView(contextTag)
        question = EditText(this).apply {
            hint = "Ask about this note"
            setHintTextColor(Color.parseColor(Ink.FAINT))
            setTextColor(Color.parseColor(Ink.TEXT))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        inputBar.addView(question, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sendButton = TextView(this).apply {
            text = "↑"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(Ink.BLACK))
            background = surface(Ink.TEXT, 50)
            isClickable = true
            setOnClickListener { if (practising) submitPractice() else askQuestion() }
        }
        inputBar.addView(sendButton, LinearLayout.LayoutParams(dp(40), dp(40)))
        readyBox.addView(inputBar.also { it.fillWidth() })
        panel.addView(readyBox.also { it.fillWidth() })
        return panel
    }

    private fun openNote(id: String) {
        val note = book.find(id) ?: return
        openId = id
        book = book.opened(id)
        suppressSave = true
        editor.setText(note.body)
        suppressSave = false
        savedText.text = ""
        wordsText.text = "${NoteRules.wordCount(note.body)} WORDS"
        tutorStatus.text = ""
        answerText.text = if (lastAnswerNoteId == id) lastAnswer else ""
        answerScroll.visibility = if (answerText.text.isNotEmpty()) View.VISIBLE else View.GONE
        library.visibility = View.GONE
        libraryTabs.visibility = View.GONE
        workspace.visibility = View.VISIBLE
        endPractice()
        updateContextTag()
        updateFocusControls()
        refreshTutor()
        persist()
    }

    private fun closeNote() {
        saveNow()
        saveContext()
        showLibrary()
    }

    // Test hooks for device tests in this module. They run the same code paths as the buttons.
    internal fun createNoteForTest() = newNote()
    internal fun setBodyForTest(text: String) = editor.setText(text)
    internal fun saveForTest() = saveNow()
    internal fun openIdForTest(): String? = openId
    internal fun editorTextForTest(): String = editor.text.toString()

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
        savedText.text = "SAVED"
        wordsText.text = "${NoteRules.wordCount(body)} WORDS"
        persist()
    }

    private fun deleteOpen() {
        val id = openId ?: return
        main.removeCallbacks(autoSave)
        book = book.remove(id)
        persist()
        showLibrary()
    }

    /** Starts focus for the open note. The 2-minute test starts at once when the lock is already ready. */
    private fun startFocus(preset: Preset, autostart: Boolean) {
        saveNow()
        if (SessionStore.isActive(this)) return
        val note = openId?.let { book.find(it) } ?: return
        startActivity(
            Intent(this, SetupActivity::class.java)
                .putExtra(SetupActivity.EXTRA_PRESET, preset.id)
                .putExtra(SetupActivity.EXTRA_LINE, StudyFocus.lineFor(note.title))
                .putExtra(SetupActivity.EXTRA_NOTE, note.id)
                .putExtra(SetupActivity.EXTRA_AUTOSTART, autostart),
        )
    }

    // --- Practise: recall from the note's own sentences, no AI call -----------------------------

    private fun startPractice() {
        val note = openId?.let { book.find(it) } ?: return
        val questions = ClozePractice.questions(note.body)
        if (questions.isEmpty()) {
            answerText.text = "Write a few full sentences in this note to practise."
            answerScroll.visibility = View.VISIBLE
            return
        }
        practice = questions
        practiceIndex = 0
        practiceCorrect = 0
        practiceMissed.clear()
        practiceAwaitingNext = false
        practising = true
        contextTag.text = "PRACTISE"
        question.hint = "Your answer"
        showPracticeQuestion()
    }

    private fun showPracticeQuestion() {
        val current = practice[practiceIndex]
        answerText.text = "${practiceIndex + 1} of ${practice.size}\n\n${current.prompt}"
        answerScroll.visibility = View.VISIBLE
        question.setText("")
    }

    private fun submitPractice() {
        if (practiceAwaitingNext) {
            practiceAwaitingNext = false
            practiceIndex++
            if (practiceIndex < practice.size) showPracticeQuestion() else finishPractice()
            return
        }
        val typed = question.text.toString()
        if (typed.isBlank()) return
        val current = practice[practiceIndex]
        val right = ClozePractice.correct(typed, current.answer)
        if (right) practiceCorrect++ else practiceMissed.add(current)
        practiceAwaitingNext = true
        answerText.text = (if (right) "Correct." else "Not quite. The word is ${current.answer}.") +
            "\n\n" + current.sentence + "\n\nPress ↑ for the next one."
        question.setText("")
    }

    /** The words this note repeats most. Counted on the device; no AI call. */
    private fun showKeyTerms() {
        endPractice()
        val note = openId?.let { book.find(it) } ?: return
        val terms = KeyTerms.top(note.body)
        answerText.text = if (terms.isEmpty()) "No repeated terms yet." else
            terms.joinToString("\n") { "${it.word}   ${it.count}" }
        answerScroll.visibility = View.VISIBLE
    }

    private fun finishPractice() {
        val revisit = if (practiceMissed.isEmpty()) "" else
            "\n\nRevisit:\n" + practiceMissed.joinToString("\n\n") { it.sentence }
        answerText.text = "$practiceCorrect of ${practice.size} correct.$revisit"
        endPractice()
    }

    private fun endPractice() {
        if (!practising) return
        practising = false
        practiceAwaitingNext = false
        question.hint = "Ask about this note"
        updateContextTag()
    }

    /** The context pill says what the tutor will read: the selection, or the whole note. */
    private fun updateContextTag() {
        if (practising) return
        val selected = selectedText().length
        contextTag.text = if (selected > 0) "SELECTION · $selected" else "NOTE"
    }

    // --- Tutor -------------------------------------------------------------------------------

    private enum class TutorState { NOT_CONFIGURED, SIGN_IN, READY }

    /** Chooses what the tutor shows. The sign-in check reads the Keystore off the main thread. */
    private fun refreshTutor() {
        val problem = BackendConfig.problem()
        if (problem != null) {
            showTutor(TutorState.NOT_CONFIGURED)
            tutorNotice.text = "Offline · backend not configured in this build. Notes and focus work without it. ($problem)"
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
        endPractice()
        updateContextTag()
        send(StudyPrompts.requestFor(action, currentMaterial()))
    }

    private fun askQuestion() {
        updateContextTag()
        send(StudyPrompts.questionRequest(currentMaterial(), question.text.toString()))
    }

    private fun send(request: AiRequest) {
        AiRules.validate(request)?.let {
            tutorStatus.text = it.uppercase()
            return
        }
        val noteId = openId
        setBusy(true)
        tutorStatus.text = "THINKING…"
        client.send(request) { result ->
            setBusy(false)
            show(result, noteId)
        }
    }

    private fun setBusy(busy: Boolean) {
        actionPills.forEach { it.isEnabled = !busy }
        sendButton.isEnabled = !busy
        sendButton.alpha = if (busy) 0.4f else 1f
    }

    private fun show(result: AiResult, noteId: String?) {
        when (result) {
            is AiResult.Success -> {
                tutorStatus.text = ""
                lastAnswerNoteId = noteId
                lastAnswer = result.text
                // An answer for a note the student has since left is kept, but not shown on screen.
                if (noteId == openId) {
                    answerText.text = result.text
                    answerScroll.visibility = View.VISIBLE
                }
                saveContext()
            }
            is AiResult.Unavailable -> {
                if (result.reason == Unavailability.NOT_SIGNED_IN) {
                    showTutor(TutorState.SIGN_IN)
                    tutorNotice.text = "Your session has ended. Sign in again to use the tutor."
                }
                tutorStatus.text = unavailableText(result.reason).uppercase()
            }
            is AiResult.QuotaExceeded -> tutorStatus.text = "Daily limit reached · resets at midnight (Kampala)"
            is AiResult.Rejected -> tutorStatus.text = result.message.uppercase()
            is AiResult.Failed -> tutorStatus.text = result.message.uppercase()
        }
    }

    private fun unavailableText(reason: Unavailability): String = when (reason) {
        Unavailability.NOT_SIGNED_IN -> "Sign in to use the tutor."
        Unavailability.OFFLINE -> "No connection. Your notes are still here."
        Unavailability.NOT_CONFIGURED -> "Tutor not set up in this build."
        Unavailability.SERVICE_DOWN -> "Tutor is switched off right now."
    }

    private fun onSignIn() {
        signInStatus.text = "SIGNING IN…"
        signInButton.isEnabled = false
        auth.signIn(manager, email.text.toString(), password.text.toString()) { message ->
            password.setText("")
            signInButton.isEnabled = true
            if (message == null) {
                signInStatus.text = ""
                refreshTutor()
            } else {
                signInStatus.text = message.uppercase()
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

    private fun underlined(hint: String, type: Int): EditText = EditText(this).apply {
        this.hint = hint
        inputType = type
        setHintTextColor(Color.parseColor(Ink.FAINT))
        setTextColor(Color.parseColor(Ink.TEXT))
        setBackgroundColor(Color.TRANSPARENT)
        textSize = 16f
        setPadding(0, dp(12), 0, dp(12))
    }

    private fun spacer(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private companion object {
        const val AUTO_SAVE_DELAY_MS = 700L
        const val PREFS = "study-workspace"
        const val CONTEXT_KEY = "context"
    }
}
