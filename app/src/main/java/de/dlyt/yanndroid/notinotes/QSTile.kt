package de.dlyt.yanndroid.notinotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews

class QSTile : TileService() {

    private val context: Context = this

    override fun onCreate() {
        super.onCreate()
        Notification.createNotificationChannel(context)
        Notification.showAll(context)
    }

    override fun onStartListening() {
        super.onStartListening()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.subtitle = Notes.getNotes(context).size.toString()
            qsTile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val dialogIntent = Intent(context, DialogActivity::class.java)
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        dialogIntent.action = ActionReceiver.ACTION_EDIT
        dialogIntent.putExtra(ActionReceiver.EXTRA_NOTE, Notes.Note())
        startActivityAndCollapse(dialogIntent)
    }

    /** #### Detail view (samsung only) #### **/

    fun semGetSettingsIntent(): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/Yanndroid/NotiNotes")
        ) //todo AboutActivity?

    fun semIsToggleButtonExists(): Boolean = false
    fun semGetDetailViewTitle(): CharSequence = this.getString(R.string.app_name)
    fun semGetDetailView(): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.qs_detail_view)
        val adapter = LinearLayoutAdapter(remoteViews, R.id.qs_detail_list)

        for (note in Notes.getNotes(context)) {
            val noteView = RemoteViews(context.packageName, R.layout.qs_detail_list_item)
            noteView.setTextViewText(R.id.qs_list_item_title, note.title)
            noteView.setTextColor(R.id.qs_list_item_title, note.color)
            noteView.setTextViewText(R.id.qs_list_item_content, note.content)
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_container,
                ActionReceiver.getPendingIntent(context, note, ActionReceiver.ACTION_SHOW)
            )
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_edit,
                ActionReceiver.getPendingIntent(context, note, ActionReceiver.ACTION_EDIT)
            )
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_delete,
                ActionReceiver.getPendingIntent(context, note, ActionReceiver.ACTION_DELETE)
            )
            adapter.addView(noteView)
        }

        remoteViews.setOnClickPendingIntent(
            R.id.qs_detail_add,
            ActionReceiver.getPendingIntent(
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