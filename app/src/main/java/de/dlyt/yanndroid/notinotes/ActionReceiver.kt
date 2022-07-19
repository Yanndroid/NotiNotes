package de.dlyt.yanndroid.notinotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {

    companion object {
        val ACTION_EDIT = "de.dlyt.yanndroid.notinotes.EDIT"
        val ACTION_DELETE = "de.dlyt.yanndroid.notinotes.DELETE"
        val ACTION_SHOW = "de.dlyt.yanndroid.notinotes.SHOW"
        val EXTRA_NOTE = "intent_note"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val note: Notes.Note = (intent.getSerializableExtra(EXTRA_NOTE) ?: return) as Notes.Note
        val notes = Notes(context)
        when (intent.action) {
            ACTION_EDIT -> notes.editNotePopup(note)
            ACTION_DELETE -> notes.deleteNote(note)
            ACTION_SHOW -> notes.showNoteDialog(note)
        }
    }
}