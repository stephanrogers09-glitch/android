package app.lockedin.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.view.Gravity
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.lockedin.core.notes.Note
import app.lockedin.core.notes.NoteBook
import app.lockedin.core.notes.NoteRules
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Study notes. Notes stay on this phone and work without an account. The open note can be sent to
 * the AI helper, so its answers are about the material the student is studying. The note they were
 * writing is offered again at the top of the list, so they can carry on where they stopped.
 */
class NotesActivity : Activity() {
    private lateinit var listBlock: LinearLayout
    private lateinit var resumeHolder: LinearLayout
    private lateinit var noteList: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var editorBlock: LinearLayout
    private lateinit var editor: EditText
    private lateinit var savedText: TextView

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val autoSave = Runnable { saveNow() }

    private var book = NoteBook()
    private var openId: String? = null

    /** True while the editor is filled from a note, so filling it does not count as an edit. */
    private var suppressSave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor(COLOR_BG)) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }
        scroll.addView(page)

        page.addView(TextView(this).apply {
            text = "STUDY NOTES"
            quiet(this@NotesActivity, 13f, Color.parseColor(COLOR_MUTED))
            letterSpacing = 0.18f
        })

        listBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resumeHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listBlock.addView(resumeHolder.also { it.fillWidth() })
        listBlock.addView(Button(this).apply {
            text = "New note"
            setOnClickListener { newNote() }
            outline(this@NotesActivity)
        }.also { it.fillWidth() })
        emptyText = TextView(this).apply {
            text = "No notes yet. Write one, then ask the AI helper about it."
            quiet(this@NotesActivity, 15f)
        }
        listBlock.addView(emptyText.also { it.fillWidth() })
        noteList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listBlock.addView(noteList.also { it.fillWidth() })
        page.addView(listBlock)

        editorBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Back"
            setOnClickListener { closeEditor() }
            outline(this@NotesActivity)
        })
        actions.addView(Button(this).apply {
            text = "Ask AI"
            setOnClickListener { askAi() }
            outline(this@NotesActivity)
        })
        actions.addView(Button(this).apply {
            text = "Delete"
            setOnClickListener { deleteOpen() }
            outline(this@NotesActivity)
        })
        editorBlock.addView(actions.also { it.fillWidth() })
        savedText = TextView(this).apply { quiet(this@NotesActivity, 13f) }
        editorBlock.addView(savedText.also { it.fillWidth() })
        editor = EditText(this).apply {
            hint = "Write your notes here"
            textSize = 18f
            minLines = 14
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
        editorBlock.addView(editor.also { it.fillWidth() })
        editorBlock.visibility = View.GONE
        page.addView(editorBlock)

        listBlock.visibility = View.GONE
        setContentView(scroll)
        io.execute {
            val loaded = NotesStore.load(applicationContext)
            main.post {
                book = loaded
                showList()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(autoSave)
        io.shutdown()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (editorBlock.visibility == View.VISIBLE) closeEditor() else super.onBackPressed()
    }

    private fun showList() {
        openId = null
        editorBlock.visibility = View.GONE
        listBlock.visibility = View.VISIBLE
        resumeHolder.removeAllViews()
        noteList.removeAllViews()

        val last = book.lastOpened()
        if (last != null) {
            resumeHolder.addView(Button(this).apply {
                text = "Continue: ${last.title}"
                setOnClickListener { openNote(last.id) }
                outline(this@NotesActivity)
            }.also { it.fillWidth() })
        }
        val notes = book.sorted()
        emptyText.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
        for (note in notes) {
            noteList.addView(Button(this).apply {
                text = note.title
                isAllCaps = false
                setOnClickListener { openNote(note.id) }
                outline(this@NotesActivity)
            }.also { it.fillWidth() })
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

    private fun openNote(id: String) {
        val note = book.find(id) ?: return
        openId = id
        book = book.opened(id)
        suppressSave = true
        editor.setText(note.body)
        suppressSave = false
        savedText.text = ""
        listBlock.visibility = View.GONE
        editorBlock.visibility = View.VISIBLE
        persist()
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
        persist()
    }

    private fun closeEditor() {
        saveNow()
        showList()
    }

    private fun deleteOpen() {
        val id = openId ?: return
        main.removeCallbacks(autoSave)
        book = book.remove(id)
        persist()
        showList()
    }

    private fun askAi() {
        saveNow()
        startActivity(
            Intent(this, AiSessionActivity::class.java)
                .putExtra(AiSessionActivity.EXTRA_MATERIAL, editor.text.toString()),
        )
    }

    /** Writes a snapshot of the notes on the background thread. Writes run in order. */
    private fun persist() {
        val snapshot = book
        io.execute { NotesStore.save(applicationContext, snapshot) }
    }

    private companion object {
        const val AUTO_SAVE_DELAY_MS = 700L
    }
}
