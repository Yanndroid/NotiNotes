package de.dlyt.yanndroid.notinotes

import android.content.Context
import android.content.res.ColorStateList
import android.widget.CheckBox
import android.widget.RadioGroup
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.util.stream.Collectors

class Notes(val context: Context) {

    companion object {
        val NOTIFICATION_GROUP = "de.dlyt.yanndroid.notinotes.NOTES_GROUP"
        val NOTIFICATION_GROUP_HOLDER = -1
        val NOTIFICATION_CHANNEL = "1234"

        val COLORS = intArrayOf(
            R.color.color_1,
            R.color.color_2,
            R.color.color_3,
            R.color.color_4,
            R.color.color_5,
            R.color.color_6,
            R.color.color_7,
            R.color.color_8
        )
        val RBIDS = intArrayOf(
            R.id.color_1,
            R.id.color_2,
            R.id.color_3,
            R.id.color_4,
            R.id.color_5,
            R.id.color_6,
            R.id.color_7,
            R.id.color_8
        )
    }

    class Note(id: Int = -1) :
        Serializable {
        var title: String? = null
        var content: String? = null
        var colorIndex: Int = 0
        var id: Int = 0
        var secret: Boolean = false
        var locked: Boolean = true

        init {
            this.id = id
        }
    }

    var list: ArrayList<Note> = ArrayList()

    init {
        loadNotesFromSP()
        for (note in list) showNotification(note)
    }

    fun loadNotesFromSP() {
        list = Gson().fromJson(
            context.getSharedPreferences("sp", Context.MODE_PRIVATE).getString("notes", "[]"),
            object : TypeToken<ArrayList<Note>>() {}.type
        )
    }

    fun saveNotesToSP() {
        context.getSharedPreferences("sp", Context.MODE_PRIVATE).edit()
            .putString("notes", Gson().toJson(list)).apply()
    }

    fun saveNote(note: Note) {
        loadNotesFromSP()
        if (note.id == -1) note.id = generateNewNoteID()
        for (i in list.indices) {
            if (note.id == list[i].id) {
                list[i] = note
                saveNotesToSP()
                showNotification(note)
                return
            }
        }
        list.add(note)
        saveNotesToSP()
        showNotification(note)
    }

    fun deleteNote(note: Note) {
        loadNotesFromSP()
        for (i in list.indices) {
            if (note.id == list[i].id) {
                list.removeAt(i)
                saveNotesToSP()
                cancelNotification(note)
                return
            }
        }
    }

    private fun generateNewNoteID(): Int {
        val takenIDs = list.stream().map { note -> note.id }.collect(Collectors.toList())
        var newID = 0
        while (takenIDs.contains(newID)) newID++
        return newID
    }

    /** #### Notifications #### **/

    private fun showNotification(note: Note) {
        val nmc = NotificationManagerCompat.from(context)
        nmc.notify( //for some reason this in needed to make grouping work
            NOTIFICATION_GROUP_HOLDER, NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_note)
                .setGroup(NOTIFICATION_GROUP)
                .setGroupSummary(true)
                .build()
        )
        nmc.notify(
            note.id, NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setStyle(NotificationCompat.BigTextStyle())
                .setOngoing(note.locked)
                .setVisibility(if (note.secret) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC)
                .setGroup(NOTIFICATION_GROUP)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle(note.title)
                .setContentText(note.content)
                .addAction(
                    R.drawable.ic_edit,
                    context.getString(R.string.edit),
                    Utils.getPendingIntent(context, note, ActionReceiver.ACTION_EDIT)
                )
                .addAction(
                    R.drawable.ic_delete,
                    context.getString(R.string.del),
                    Utils.getPendingIntent(context, note, ActionReceiver.ACTION_DELETE)
                )
                .setContentIntent(Utils.getPendingIntent(context, note, ActionReceiver.ACTION_SHOW))
                .setDeleteIntent(
                    Utils.getPendingIntent(
                        context,
                        note,
                        ActionReceiver.ACTION_DELETE
                    )
                )
                .setColor(context.getColor(COLORS[note.colorIndex]))
                .build()
        )
    }

    private fun cancelNotification(note: Note) {
        val nmc = NotificationManagerCompat.from(context)
        nmc.cancel(note.id)
        if (list.size == 0) nmc.cancel(NOTIFICATION_GROUP_HOLDER)
    }


    /** #### Dialogs #### **/

    fun editNotePopup(note: Note) {
        if (Utils.requestPermission(context)) return
        val editDialog = Dialog(context, R.layout.popup_edit_layout, note, note.title)

        val checkHOL = editDialog.pView.findViewById<CheckBox>(R.id.check_hol)
            .also { it.isChecked = note.secret }
        val checkLock = editDialog.pView.findViewById<CheckBox>(R.id.check_lock)
            .also { it.isChecked = note.locked }

        val pColorPicker = editDialog.pView.findViewById<RadioGroup>(R.id.color_picker).also {
            it.setOnCheckedChangeListener { _, i ->
                val color = context.getColor(COLORS[RBIDS.indexOf(i)])
                editDialog.pPos.setTextColor(color)
                editDialog.pNeg.setTextColor(color)
                editDialog.pIcon.setColorFilter(color)
                checkHOL.buttonTintList = ColorStateList.valueOf(color)
                checkLock.buttonTintList = ColorStateList.valueOf(color)
            }
            it.check(RBIDS[note.colorIndex])
        }

        editDialog.pNeg.setOnClickListener { editDialog.dismiss() }
        editDialog.pPos.setOnClickListener {
            note.title = editDialog.pTitle.text.toString()
            note.content = editDialog.pNote.text.toString()
            note.colorIndex = RBIDS.indexOf(pColorPicker.checkedRadioButtonId)
            note.secret = checkHOL.isChecked
            note.locked = checkLock.isChecked
            saveNote(note)
            editDialog.dismiss()
        }
    }

    fun showNoteDialog(note: Note) {
        if (Utils.requestPermission(context)) return
        val showNoteDialog = Dialog(context, R.layout.popup_show_layout, note, note.title)
        showNoteDialog.pNeg.setOnClickListener {
            editNotePopup(note)
            showNoteDialog.dismiss()
        }
        showNoteDialog.pPos.setOnClickListener {
            deleteNote(note)
            showNoteDialog.dismiss()
        }
    }

}