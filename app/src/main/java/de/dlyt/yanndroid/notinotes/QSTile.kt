package de.dlyt.yanndroid.notinotes

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.util.stream.Collectors

class QSTile : TileService() {

    private val INTENT_EXTRA = "intent_note"
    private val NOTIFICATION_GROUP = "de.dlyt.yanndroid.notinotes.NOTES_GROUP"
    private val NOTIFICATION_GROUP_HOLDER = -1
    private val NOTIFICATION_CHANNEL = "1234"
    private val ACTION_EDIT_NOTE = "de.dlyt.yanndroid.notinotes.EDIT_NOTE"
    private val ACTION_DELETE_NOTE = "de.dlyt.yanndroid.notinotes.DELETE_NOTE"
    private val ACTION_SHOW_NOTE = "de.dlyt.yanndroid.notinotes.SHOW_NOTE"

    var COLORS = intArrayOf(
        R.color.color_1,
        R.color.color_2,
        R.color.color_3,
        R.color.color_4,
        R.color.color_5,
        R.color.color_6,
        R.color.color_7,
        R.color.color_8
    )
    var RBIDS = intArrayOf(
        R.id.color_1,
        R.id.color_2,
        R.id.color_3,
        R.id.color_4,
        R.id.color_5,
        R.id.color_6,
        R.id.color_7,
        R.id.color_8
    )

    private val context: Context = this
    private var notes: ArrayList<Note> = ArrayList()
    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val note: Note = (intent.getSerializableExtra(INTENT_EXTRA) ?: return) as Note
            when (intent.action) {
                ACTION_EDIT_NOTE -> editNotePopup(note)
                ACTION_DELETE_NOTE -> deleteNoteDialog(note)
                ACTION_SHOW_NOTE -> showNoteDialog(note)
            }
        }
    }

    class Note(title: String?, content: String?, colorIndex: Int, id: Int) : Serializable {
        var title = title
        var content = content
        var colorIndex = colorIndex
        var id = id
    }

    private fun saveNotesToSP() = getSharedPreferences("sp", Context.MODE_PRIVATE).edit()
        .putString("notes", Gson().toJson(notes)).apply()

    private fun loadNotesFromSP() {
        notes = Gson().fromJson(
            getSharedPreferences("sp", Context.MODE_PRIVATE).getString("notes", "[]"),
            object : TypeToken<ArrayList<Note>>() {}.type
        )
    }

    override fun onCreate() {
        super.onCreate()
        requestPermission()
        createNotificationChannel()
        loadNotesFromSP()
        for (note in notes) showNotification(note)

        registerReceiver(mBroadcastReceiver, IntentFilter().also {
            it.addAction(ACTION_EDIT_NOTE)
            it.addAction(ACTION_DELETE_NOTE)
            it.addAction(ACTION_SHOW_NOTE)
        })
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        NotificationManagerCompat.from(this).cancelAll()
    }

    override fun onStartListening() {
        super.onStartListening()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.subtitle = notes.size.toString()
            qsTile.updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveNotesToSP()
        unregisterReceiver(mBroadcastReceiver)
    }

    override fun onClick() {
        super.onClick()
        editNotePopup(Note(null, null, 0, generateNewNoteID()))
    }

    private fun saveNote(note: Note) {
        loadNotesFromSP()
        for (i in notes.indices) {
            if (note.id == notes[i].id) {
                notes[i] = note
                saveNotesToSP()
                showNotification(note)
                return
            }
        }
        notes.add(note)
        saveNotesToSP()
        showNotification(note)
    }

    private fun deleteNote(note: Note) {
        loadNotesFromSP()
        for (i in notes.indices) {
            if (note.id == notes[i].id) {
                notes.removeAt(i)
                saveNotesToSP()
                cancelNotification(note)
                return
            }
        }
    }

    private fun showNotification(note: Note) {
        val nmc = NotificationManagerCompat.from(this)
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
                .setOngoing(true)
                .setGroup(NOTIFICATION_GROUP)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle(note.title)
                .setContentText(note.content)
                .addAction(
                    R.drawable.ic_edit,
                    getString(R.string.edit),
                    getPendingIntent(note, ACTION_EDIT_NOTE)
                )
                .addAction(
                    R.drawable.ic_delete,
                    getString(R.string.del),
                    getPendingIntent(note, ACTION_DELETE_NOTE)
                )
                .setContentIntent(getPendingIntent(note, ACTION_SHOW_NOTE))
                .setColor(getColor(COLORS[note.colorIndex]))
                .build()
        )
    }

    private fun cancelNotification(note: Note) {
        val nmc = NotificationManagerCompat.from(this)
        nmc.cancel(note.id)
        if (notes.size == 0) nmc.cancel(NOTIFICATION_GROUP_HOLDER)
    }

    private fun editNotePopup(note: Note) {
        if (!Settings.canDrawOverlays(context)) return
        val deleteDialog = Dialog(R.layout.popup_edit_layout, note, note.title)
        val pColorPicker = deleteDialog.pView.findViewById<RadioGroup>(R.id.color_picker).also {
            it.setOnCheckedChangeListener { _, i ->
                deleteDialog.pPos.setTextColor(getColor(COLORS[RBIDS.indexOf(i)]))
                deleteDialog.pNeg.setTextColor(getColor(COLORS[RBIDS.indexOf(i)]))
                deleteDialog.pIcon.setColorFilter(getColor(COLORS[RBIDS.indexOf(i)]))
            }
            it.check(RBIDS[note.colorIndex])
        }

        deleteDialog.pNeg.setOnClickListener { deleteDialog.dismiss() }
        deleteDialog.pPos.setOnClickListener {
            note.title = deleteDialog.pTitle.text.toString()
            note.content = deleteDialog.pNote.text.toString()
            note.colorIndex = RBIDS.indexOf(pColorPicker.checkedRadioButtonId)
            saveNote(note)
            deleteDialog.dismiss()
        }
    }

    private fun deleteNoteDialog(note: Note) {
        if (!Settings.canDrawOverlays(context)) return
        val deleteDialog =
            Dialog(R.layout.popup_show_layout, note, getString(R.string.del_x, note.title))
        deleteDialog.pNeg.setText(R.string.cancel)
        deleteDialog.pNeg.setOnClickListener { deleteDialog.dismiss() }
        deleteDialog.pPos.setOnClickListener {
            deleteNote(note)
            deleteDialog.dismiss()
        }
    }

    private fun showNoteDialog(note: Note) {
        if (!Settings.canDrawOverlays(context)) return
        val deleteDialog = Dialog(R.layout.popup_show_layout, note, note.title)
        deleteDialog.pNeg.setOnClickListener {
            editNotePopup(note)
            deleteDialog.dismiss()
        }
        deleteDialog.pPos.setOnClickListener {
            deleteNoteDialog(note)
            deleteDialog.dismiss()
        }
    }

    inner class Dialog(layoutRes: Int, note: Note, title: String?) {
        private val windowManager: WindowManager
        val pView: View
        val pTitle: TextView
        val pNote: TextView
        val pNeg: Button
        val pPos: Button
        val pIcon: ImageView

        init {
            closePanel()
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
            )
            params.windowAnimations = R.style.PopupAnimStyle
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            params.dimAmount = 0.35f
            params.gravity = Gravity.BOTTOM

            pView = LayoutInflater.from(context).inflate(layoutRes, null)
            windowManager.addView(pView, params)
            pView.setOnClickListener { dismiss() }

            pTitle = pView.findViewById<TextView>(R.id.pTitle).also { it.text = title }
            pNote = pView.findViewById<TextView>(R.id.pNote).also { it.text = note.content }
            pNeg = pView.findViewById<Button>(R.id.pNeg)
                .also { it.setTextColor(getColor(COLORS[note.colorIndex])) }
            pPos = pView.findViewById<Button>(R.id.pPos)
                .also { it.setTextColor(getColor(COLORS[note.colorIndex])) }
            pIcon = pView.findViewById<ImageView>(R.id.pIcon)
                .also { it.setColorFilter(getColor(COLORS[note.colorIndex])) }
        }

        fun dismiss() = windowManager.removeView(pView)
    }

    private fun requestPermission() {
        if (!Settings.canDrawOverlays(this)) {
            closePanel()
            Toast.makeText(context, R.string.permission_toast, Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                getString(R.string.notes),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun generateNewNoteID(): Int {
        val takenIDs = notes.stream().map { note -> note.id }.collect(Collectors.toList())
        var newID = 0
        while (takenIDs.contains(newID)) newID++
        return newID
    }

    private fun getPendingIntent(note: Note, action: String): PendingIntent {
        val intent = Intent(action)
        intent.putExtra(INTENT_EXTRA, note)
        return PendingIntent.getBroadcast(
            context,
            note.id,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @SuppressLint("WrongConstant")
    private fun closePanel() { //won't work for a12+
        try {
            Class.forName("android.app.StatusBarManager").getMethod("collapsePanels")
                .invoke(context.getSystemService("statusbar"))
        } catch (e: Exception) {
            Log.e("closePanel", e.message.toString())
            try {
                context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            } catch (e: Exception) {
                Log.e("closePanel", e.message.toString())
            }
        }
    }

    // #### detail view (samsung only) ####

    fun semGetSettingsIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yanndroid/NotiNotes"))

    fun semIsToggleButtonExists(): Boolean = false
    fun semGetDetailViewTitle(): CharSequence = this.getString(R.string.app_name)
    fun semGetDetailView(): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.qs_detail_view)
        val adapter = LinearLayoutAdapter(remoteViews, R.id.qs_detail_list)

        for (note in notes) {
            val noteView = RemoteViews(context.packageName, R.layout.qs_detail_list_item)
            noteView.setTextViewText(R.id.qs_list_item_title, note.title)
            noteView.setTextColor(R.id.qs_list_item_title, getColor(COLORS[note.colorIndex]))
            noteView.setTextViewText(R.id.qs_list_item_content, note.content)
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_container,
                getPendingIntent(note, ACTION_SHOW_NOTE)
            )
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_edit,
                getPendingIntent(note, ACTION_EDIT_NOTE)
            )
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_delete,
                getPendingIntent(note, ACTION_DELETE_NOTE)
            )
            adapter.addView(noteView)
        }

        remoteViews.setOnClickPendingIntent(
            R.id.qs_detail_add,
            getPendingIntent(Note(null, null, 0, generateNewNoteID()), ACTION_EDIT_NOTE)
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