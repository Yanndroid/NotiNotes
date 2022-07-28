package de.dlyt.yanndroid.notinotes

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.picker.app.SeslColorPickerDialog

class DialogActivity : AppCompatActivity() {

    private val mContext = this
    private lateinit var mKeyguardManager: KeyguardManager
    private var mEditMode = false
    private lateinit var mNote: Notes.Note

    private lateinit var icon: ImageView
    private lateinit var titleText: TextView
    private lateinit var titleEdit: TextView
    private lateinit var colorPick: ImageView
    private lateinit var editLayout: LinearLayout
    private lateinit var checkHOL: CheckBox
    private lateinit var checkLock: CheckBox
    private lateinit var noteText: TextView
    private lateinit var noteEdit: TextView
    private lateinit var buttonNeutral: Button
    private lateinit var buttonNegative: Button
    private lateinit var buttonPositive: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        mNote = (intent.getSerializableExtra(ActionReceiver.EXTRA_NOTE) ?: finish()) as Notes.Note

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog)
        window.setGravity(Gravity.BOTTOM)

        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        mKeyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        mKeyguardManager.requestDismissKeyguard(this, null)

        icon = findViewById(R.id.dialog_icon)
        titleText = findViewById<TextView?>(R.id.dialog_title).apply { text = mNote.title }
        titleEdit = findViewById<TextView?>(R.id.dialog_title_edit).apply { text = mNote.title }
        colorPick = findViewById(R.id.dialog_color_picker)
        editLayout = findViewById(R.id.dialog_edit_layout)
        checkHOL = findViewById<CheckBox?>(R.id.dialog_check_hol).apply { isChecked = mNote.secret }
        checkLock =
            findViewById<CheckBox?>(R.id.dialog_check_lock).apply { isChecked = mNote.locked }
        noteText = findViewById<TextView?>(R.id.dialog_note).apply { text = mNote.content }
        noteEdit = findViewById<TextView?>(R.id.dialog_note_edit).apply { text = mNote.content }
        buttonNeutral = findViewById(android.R.id.button3)
        buttonNegative = findViewById(android.R.id.button2)
        buttonPositive = findViewById(android.R.id.button1)

        setColor(mNote.color)

        when (intent.action) {
            ActionReceiver.ACTION_SHOW -> setLayout(mNote, false)
            ActionReceiver.ACTION_EDIT -> setLayout(mNote, true)
            else -> finish()
        }
    }

    private fun setLayout(note: Notes.Note, editMode: Boolean) {
        mEditMode = editMode
        setFinishOnTouchOutside(!editMode)
        titleText.isGone = editMode
        titleEdit.isGone = !editMode
        colorPick.isGone = !editMode
        editLayout.isGone = !editMode
        noteText.isGone = editMode
        noteEdit.isGone = !editMode
        buttonNeutral.isGone = editMode
        findViewById<View>(R.id.sem_divider2).isGone = editMode
        findViewById<View>(R.id.sem_divider1).isVisible = true

        colorPick.setOnClickListener {
            SeslColorPickerDialog(
                mContext,
                { color ->
                    note.color = color
                    setColor(color)
                }, note.color
            ).show()
        }

        if (editMode) {
            buttonNegative.apply {
                setText(R.string.cancel)
                setOnClickListener {
                    mEditMode = false
                    finish()
                }
            }
            buttonPositive.apply {
                setText(R.string.save)
                setTextColor(getColor(androidx.appcompat.R.color.sesl_functional_green_light))
                setOnClickListener {
                    Notes.saveNote(mContext, note.apply {
                        title = titleEdit.text.toString()
                        content = noteEdit.text.toString()
                        secret = checkHOL.isChecked
                        locked = checkLock.isChecked
                    })
                    mEditMode = false
                    finish()
                }
            }
        } else {
            buttonNeutral.apply {
                setText(R.string.cancel)
                setOnClickListener { finish() }
            }
            buttonNegative.apply {
                setText(R.string.edit)
                setOnClickListener { setLayout(note, true) }
            }
            buttonPositive.apply {
                setText(R.string.del)
                setTextColor(getColor(androidx.appcompat.R.color.sesl_functional_red_light))
                setOnClickListener {
                    Notes.deleteNote(mContext, note)
                    finish()
                }
            }
        }
    }

    private fun setColor(color: Int) {
        icon.setColorFilter(color)
        titleText.setTextColor(color)
        colorPick.setColorFilter(color)
    }

    override fun onBackPressed() {
        if (!mEditMode) finish()
    }

    override fun getTheme(): Resources.Theme {
        val theme = super.getTheme()
        val outValue = TypedValue()
        theme.resolveAttribute(
            androidx.appcompat.R.attr.alertDialogTheme,
            outValue,
            true
        )
        theme.applyStyle(outValue.resourceId, true)
        return theme
    }

    override fun onPause() {
        super.onPause()
        if (mKeyguardManager.isKeyguardLocked) return
        if (mEditMode && noteEdit.text.toString() != mNote.content && !noteEdit.text.isNullOrEmpty()) {
            Notes.saveNote(mContext, mNote.apply {
                id = Notes.generateNewNoteID(mContext)
                title = titleEdit.text.toString()
                content = noteEdit.text.toString()
                secret = checkHOL.isChecked
                locked = checkLock.isChecked
            })
            Toast.makeText(mContext, R.string.saved_copy, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}