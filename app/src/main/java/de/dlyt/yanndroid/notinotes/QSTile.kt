package de.dlyt.yanndroid.notinotes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat

class QSTile : TileService() {

    private val context: Context = this
    private lateinit var notes: Notes

    override fun onCreate() {
        super.onCreate()
        notes = Notes(context)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Utils.requestPermission(this)
        createNotificationChannel()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        NotificationManagerCompat.from(this).cancelAll()
    }

    override fun onStartListening() {
        super.onStartListening()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.subtitle = notes.list.size.toString()
            qsTile.updateTile()
        }
    }

    /*override fun onDestroy() {
        super.onDestroy()
        notes.saveNotesToSP()
    }*/

    override fun onClick() {
        super.onClick()
        notes.editNotePopup(Notes.Note())
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                Notes.NOTIFICATION_CHANNEL,
                context.getString(R.string.notes),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    /** #### Detail view (samsung only) #### **/

    fun semGetSettingsIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yanndroid/NotiNotes"))

    fun semIsToggleButtonExists(): Boolean = false
    fun semGetDetailViewTitle(): CharSequence = this.getString(R.string.app_name)
    fun semGetDetailView(): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.qs_detail_view)
        val adapter = LinearLayoutAdapter(remoteViews, R.id.qs_detail_list)

        notes.loadNotesFromSP()
        for (note in notes.list) {
            val noteView = RemoteViews(context.packageName, R.layout.qs_detail_list_item)
            noteView.setTextViewText(R.id.qs_list_item_title, note.title)
            noteView.setTextColor(R.id.qs_list_item_title, getColor(Notes.COLORS[note.colorIndex]))
            noteView.setTextViewText(R.id.qs_list_item_content, note.content)
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_container,
                Utils.getPendingIntent(context, note, ActionReceiver.ACTION_SHOW)
            )
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_edit,
                Utils.getPendingIntent(context, note, ActionReceiver.ACTION_EDIT)
            )
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_delete,
                Utils.getPendingIntent(context, note, ActionReceiver.ACTION_DELETE)
            )
            adapter.addView(noteView)
        }

        remoteViews.setOnClickPendingIntent(
            R.id.qs_detail_add,
            Utils.getPendingIntent(
                context,
                Notes.Note(),
                ActionReceiver.ACTION_EDIT
            )
        )
        return remoteViews
    }

    private class LinearLayoutAdapter(root: RemoteViews, LLId: Int) {
        val root = root
        val LLId = LLId
        val views: ArrayList<RemoteViews> = ArrayList()

        fun addView(aView: RemoteViews) {
            if (views.contains(aView)) return
            views.add(aView)
            root.addView(LLId, aView)
        }
    }
}