package de.dlyt.yanndroid.notinotes

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.picker3.app.SeslColorPickerDialog


class DialogActivity : AppCompatActivity() {

    private val mContext = this
    private lateinit var mKeyguardManager: KeyguardManager
    private var mEditMode = false
    private lateinit var mNote: Notes.Note

    private lateinit var icon: ImageView
    private lateinit var titleText: TextView
    private lateinit var titleEdit: TextView
    private lateinit var colorPick: ImageView
    private lateinit var settings: ImageView
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
        settings = findViewById(R.id.dialog_settings)
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
        settings.isGone = !editMode
        noteText.isGone = editMode
        noteEdit.isGone = !editMode
        buttonNeutral.isGone = editMode
        findViewById<View>(R.id.sem_divider2).isGone = editMode
        findViewById<View>(R.id.sem_divider1).isVisible = true


        if (editMode) {
            colorPick.setOnClickListener {
                val rColors = Notes.getRecentColors(mContext)

                SeslColorPickerDialog(
                    ContextThemeWrapper(mContext, R.style.ColorDialogWidthFix),
                    { color ->
                        note.color = color
                        setColor(color)
                        Notes.saveRecentColor(mContext, rColors, color)
                    }, note.color, rColors, false
                ).show()
            }

            val popmenu = PopupMenu(mContext, settings)
            popmenu.seslSetOffset(0, -300)
            popmenu.menuInflater.inflate(R.menu.settings, popmenu.menu)
            popmenu.menu.let {
                it.findItem(R.id.settings_hide).isChecked = note.secret
                it.findItem(R.id.settings_lock).isChecked = note.locked
                it.findItem(R.id.settings_group).isChecked = note.group
                it.findItem(R.id.settings_bg_tint).isChecked = note.bg_tint
                it.findItem(R.id.settings_del_confirm).isChecked = note.del_confirm
            }
            popmenu.setOnMenuItemClickListener { item: MenuItem ->
                item.isChecked = !item.isChecked
                when (item.itemId) {
                    R.id.settings_hide -> note.secret = item.isChecked
                    R.id.settings_lock -> note.locked = item.isChecked
                    R.id.settings_group -> note.group = item.isChecked
                    R.id.settings_bg_tint -> note.bg_tint = item.isChecked
                    R.id.settings_del_confirm -> note.del_confirm = item.isChecked
                }
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
                item.actionView = View(mContext)
                false
            }
            settings.setOnClickListener { popmenu.show() }

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
                    })
                    mEditMode = false
                    finish()
                }
            }
        } else {
            noteText.setOnClickListener { setLayout(note, true) }
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
            })
            Toast.makeText(mContext, R.string.saved_copy, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}