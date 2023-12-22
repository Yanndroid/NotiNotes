package de.dlyt.yanndroid.notinotes

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {

    companion object {
        val ACTION_EDIT = "de.dlyt.yanndroid.notinotes.EDIT"
        val ACTION_DELETE = "de.dlyt.yanndroid.notinotes.DELETE"
        val ACTION_DISMISS = "de.dlyt.yanndroid.notinotes.DISMISS"
        val ACTION_SHOW = "de.dlyt.yanndroid.notinotes.SHOW"
        val EXTRA_NOTE = "intent_note"

        fun getPendingIntent(context: Context, note: Notes.Note, action: String): PendingIntent {
            val intent = Intent(context, ActionReceiver::class.java)
            intent.action = action
            intent.putExtra(EXTRA_NOTE, note)
            return PendingIntent.getBroadcast(
                context,
                note.id,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Notification.createNotificationChannel(context)
                Notification.showAll(context)
            }

            ACTION_DISMISS -> {
                val note = (intent.getSerializableExtra(EXTRA_NOTE) ?: return) as Notes.Note
                if (note.locked) { //A14 don't delete locked notes when dismissed
                    Notification.show(context, note)
                } else {
                    Notes.deleteNote(context, note)
                }
            }

            ACTION_DELETE -> {
                val note = (intent.getSerializableExtra(EXTRA_NOTE) ?: return) as Notes.Note
                Notes.deleteNote(context, note)
                //todo notify TileService Detail View
            }

            ACTION_EDIT, ACTION_SHOW -> {
                val note = (intent.getSerializableExtra(EXTRA_NOTE) ?: return) as Notes.Note
                val dialogIntent = Intent(context, DialogActivity::class.java)
                dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                dialogIntent.action = intent.action
                dialogIntent.putExtra(EXTRA_NOTE, note)
                context.startActivity(dialogIntent)
            }
        }
    }
}