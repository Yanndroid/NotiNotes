package de.dlyt.yanndroid.notinotes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


class Notification {

    companion object {

        val NOTIFICATION_GROUP = "de.dlyt.yanndroid.notinotes.NOTES_GROUP"
        val NOTIFICATION_GROUP_HOLDER = -1
        val NOTIFICATION_CHANNEL = "1234"

        fun showAll(context: Context) {
            for (note in Notes.getNotes(context)) show(context, note)
        }

        fun cancelAll(context: Context) {
            NotificationManagerCompat.from(context).cancelAll()
        }

        fun show(context: Context, note: Notes.Note) {
            val nmc = NotificationManagerCompat.from(context)
            nmc.notify( //for some reason this in needed to make grouping work
                NOTIFICATION_GROUP_HOLDER, NotificationCompat.Builder(
                    context,
                    NOTIFICATION_CHANNEL
                )
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
                        ActionReceiver.getPendingIntent(context, note, ActionReceiver.ACTION_EDIT)
                    )
                    .addAction(
                        R.drawable.ic_delete,
                        context.getString(R.string.del),
                        ActionReceiver.getPendingIntent(context, note, ActionReceiver.ACTION_DELETE)
                    )
                    .setContentIntent(
                        ActionReceiver.getPendingIntent(
                            context,
                            note,
                            ActionReceiver.ACTION_SHOW
                        )
                    )
                    .setDeleteIntent(
                        ActionReceiver.getPendingIntent(
                            context,
                            note,
                            ActionReceiver.ACTION_DELETE
                        )
                    )
                    .setColor(note.color)
                    .build()
            )
        }

        fun cancel(context: Context, note: Notes.Note) {
            val nmc = NotificationManagerCompat.from(context)
            nmc.cancel(note.id)
            if (Notes.getNotes(context).size == 0) nmc.cancel(NOTIFICATION_GROUP_HOLDER)
        }

        fun createNotificationChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    context.getString(R.string.notes),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

    }
}