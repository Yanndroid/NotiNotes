package de.dlyt.yanndroid.notinotes

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast


class Utils {

    companion object {

        fun requestPermission(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, R.string.permission_toast, Toast.LENGTH_SHORT).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$context.packageName")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            }
            return false
        }

        fun getPendingIntent(context: Context, note: Notes.Note, action: String): PendingIntent {
            val intent = Intent(context, ActionReceiver::class.java)
            intent.action = action
            intent.putExtra(ActionReceiver.EXTRA_NOTE, note)
            return PendingIntent.getBroadcast(
                context,
                note.id,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}