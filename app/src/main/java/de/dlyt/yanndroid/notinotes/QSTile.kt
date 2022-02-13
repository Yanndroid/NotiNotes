package de.dlyt.yanndroid.notinotes

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
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

    var COLORS = intArrayOf(
        R.color.color_1,
        R.color.color_2,
        R.color.color_3,
        R.color.color_4,
        R.color.color_5,
        R.color.color_6
    )
    var RBIDS = intArrayOf(
        R.id.color_1,
        R.id.color_2,
        R.id.color_3,
        R.id.color_4,
        R.id.color_5,
        R.id.color_6
    )

    private val context: Context = this
    private var notes: ArrayList<Note> = ArrayList()
    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.e("mBroadcastReceiver", intent.action.toString())
            val note: Note = (intent.getSerializableExtra(INTENT_EXTRA) ?: return) as Note
            when (intent.action) {
                ACTION_EDIT_NOTE -> editNotePopup(note)
                ACTION_DELETE_NOTE -> deleteNoteDialog(note)
            }
        }
    }

    class Note(title: String?, content: String?, colorIndex: Int, id: Int) : Serializable {
        var title = title
        var content = content
        var colorIndex = colorIndex
        var id = id
    }

    private fun saveNotesToSP() {
        getSharedPreferences("sp", Context.MODE_PRIVATE).edit()
            .putString("notes", Gson().toJson(notes)).apply()
    }

    private fun loadNotesFromSP() {
        notes = Gson().fromJson(
            getSharedPreferences("sp", Context.MODE_PRIVATE).getString("notes", "[]"),
            object : TypeToken<ArrayList<Note>>() {}.type
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        loadNotesFromSP()
        for (note in notes) showNotification(note)

        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_EDIT_NOTE)
        intentFilter.addAction(ACTION_DELETE_NOTE)
        registerReceiver(mBroadcastReceiver, intentFilter)

        requestPermission()
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

    private fun editNotePopup(note: Note) {
        if (!Settings.canDrawOverlays(context)) return
        closePanel()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            ((if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) resources.displayMetrics.widthPixels else resources.displayMetrics.heightPixels) * 0.90).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        params.windowAnimations = R.style.PopupAnimStyle
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        params.dimAmount = 0.35f
        params.gravity = Gravity.BOTTOM

        val pView = LayoutInflater.from(context).inflate(R.layout.popup_edit_layout, null)
        windowManager.addView(pView, params)

        val pTitle = pView.findViewById<EditText>(R.id.pTitle)
        val pNote = pView.findViewById<EditText>(R.id.pNote)
        val pSave = pView.findViewById<Button>(R.id.pSave)
        val pCancel = pView.findViewById<Button>(R.id.pCancel)
        val pColorPicker = pView.findViewById<RadioGroup>(R.id.color_picker)

        pTitle.setText(note.title)
        pNote.setText(note.content)
        pSave.setOnClickListener {
            note.title = pTitle.text.toString()
            note.content = pNote.text.toString()
            note.colorIndex = RBIDS.indexOf(pColorPicker.checkedRadioButtonId)
            saveNote(note)
            windowManager.removeView(pView)
        }

        pCancel.setOnClickListener { windowManager.removeView(pView) }
        pView.setOnClickListener { windowManager.removeView(pView) }

        pColorPicker.setOnCheckedChangeListener { radioGroup, i ->
            pSave.setTextColor(getColor(COLORS[RBIDS.indexOf(i)]))
            pCancel.setTextColor(getColor(COLORS[RBIDS.indexOf(i)]))
        }
        pColorPicker.check(RBIDS[note.colorIndex])
    }

    private fun deleteNoteDialog(note: Note) {
        if (!Settings.canDrawOverlays(context)) return
        closePanel()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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

        val pView = LayoutInflater.from(context).inflate(R.layout.popup_delete_layout, null)
        windowManager.addView(pView, params)

        val pTitle = pView.findViewById<TextView>(R.id.pTitle)
        val pNote = pView.findViewById<TextView>(R.id.pNote)
        val pDelete = pView.findViewById<Button>(R.id.pDelete)
        val pCancel = pView.findViewById<Button>(R.id.pCancel)

        pTitle.text = getString(R.string.del_x, note.title)
        pNote.text = note.content
        pDelete.setOnClickListener {
            deleteNote(note)
            windowManager.removeView(pView)
        }

        pCancel.setOnClickListener { windowManager.removeView(pView) }
        pView.setOnClickListener { windowManager.removeView(pView) }

        pDelete.setTextColor(getColor(COLORS[note.colorIndex]))
        pCancel.setTextColor(getColor(COLORS[note.colorIndex]))
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
                    getPendingIntent(note, false)
                )
                .addAction(
                    R.drawable.ic_delete,
                    getString(R.string.del),
                    getPendingIntent(note, true)
                )
                .setColor(getColor(COLORS[note.colorIndex]))
                .build()
        )
    }

    private fun cancelNotification(note: Note) {
        val nmc = NotificationManagerCompat.from(this)
        nmc.cancel(note.id)
        if (notes.size == 0) nmc.cancel(NOTIFICATION_GROUP_HOLDER)
    }

    // #### helper methods ####

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

    private fun getPendingIntent(note: Note, delete: Boolean): PendingIntent {
        val intent = Intent(if (delete) ACTION_DELETE_NOTE else ACTION_EDIT_NOTE)
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
                R.id.qs_list_item_edit,
                getPendingIntent(note, false)
            )
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_delete,
                getPendingIntent(note, true)
            )
            adapter.addView(noteView)
        }

        remoteViews.setOnClickPendingIntent(
            R.id.qs_detail_add,
            getPendingIntent(Note(null, null, 0, generateNewNoteID()), false)
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

    /*
    public final void semFireToggleStateChanged(boolean var1, boolean var2)
    public RemoteViews semGetDetailView()
    public CharSequence semGetDetailViewSettingButtonName()
    public CharSequence semGetDetailViewTitle()
    public Intent semGetSettingsIntent()
    public boolean semIsToggleButtonChecked()
    public boolean semIsToggleButtonExists()
    public void semSetToggleButtonChecked(boolean var1)
    public final void semUpdateDetailView()
    */
}