package de.dlyt.yanndroid.notinotes

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.core.content.FileProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.Serializable
import java.util.stream.Collectors

class QSTile : TileService() {

    private val NOTIFICATION_GROUP = "de.dlyt.yanndroid.notinotes.NOTES_GROUP"
    private val NOTIFICATION_GROUP_HOLDER = -1
    private val NOTIFICATION_CHANNEL = "1234"

    private val ACTION_EDIT_DIALOG = "de.dlyt.yanndroid.notinotes.EDIT_DIALOG"
    private val ACTION_DELETE_DIALOG = "de.dlyt.yanndroid.notinotes.DELETE_DIALOG"
    private val ACTION_DELETE_NOTE = "de.dlyt.yanndroid.notinotes.DELETE_NOTE"
    private val ACTION_SHOW_NOTE = "de.dlyt.yanndroid.notinotes.SHOW_NOTE"
    private val EXTRA_NOTE = "intent_note"

    private val ACTION_APP_UPDATE = "de.dlyt.yanndroid.notinotes.UPDATE"
    private val EXTRA_UPDATE_URL = "UPDATE_URL"
    private val EXTRA_UPDATE_VNM = "UPDATE_VNM"

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
            if (intent.action == ACTION_APP_UPDATE) {
                installUpdate(
                    intent.getStringExtra(EXTRA_UPDATE_URL),
                    intent.getStringExtra(EXTRA_UPDATE_VNM)
                )
                return
            }

            val note: Note = (intent.getSerializableExtra(EXTRA_NOTE) ?: return) as Note
            when (intent.action) {
                ACTION_EDIT_DIALOG -> editNotePopup(note)
                ACTION_DELETE_DIALOG -> deleteNoteDialog(note)
                ACTION_DELETE_NOTE -> if (!note.locked) deleteNote(note)
                ACTION_SHOW_NOTE -> showNoteDialog(note)
            }
        }
    }

    class Note(id: Int) :
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
        loadNotesFromSP()
        for (note in notes) showNotification(note)

        registerReceiver(mBroadcastReceiver, IntentFilter().also {
            it.addAction(ACTION_EDIT_DIALOG)
            it.addAction(ACTION_DELETE_DIALOG)
            it.addAction(ACTION_DELETE_NOTE)
            it.addAction(ACTION_SHOW_NOTE)
            it.addAction(ACTION_APP_UPDATE)
        })

        checkForUpdate()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        requestPermission()
        createNotificationChannel()
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
        editNotePopup(Note(generateNewNoteID()))
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


    /** #### Notifications #### **/

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
                .setOngoing(note.locked)
                .setVisibility(if (note.secret) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC)
                .setGroup(NOTIFICATION_GROUP)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle(note.title)
                .setContentText(note.content)
                .addAction(
                    R.drawable.ic_edit,
                    getString(R.string.edit),
                    getPendingIntent(note, ACTION_EDIT_DIALOG)
                )
                .addAction(
                    R.drawable.ic_delete,
                    getString(R.string.del),
                    getPendingIntent(note, ACTION_DELETE_DIALOG)
                )
                .setContentIntent(getPendingIntent(note, ACTION_SHOW_NOTE))
                .setDeleteIntent(getPendingIntent(note, ACTION_DELETE_NOTE))
                .setColor(getColor(COLORS[note.colorIndex]))
                .build()
        )
    }

    private fun cancelNotification(note: Note) {
        val nmc = NotificationManagerCompat.from(this)
        nmc.cancel(note.id)
        if (notes.size == 0) nmc.cancel(NOTIFICATION_GROUP_HOLDER)
    }


    /** #### Dialogs #### **/

    private fun editNotePopup(note: Note) {
        if (requestPermission()) return
        val editDialog = Dialog(R.layout.popup_edit_layout, note, note.title)

        val checkHOL = editDialog.pView.findViewById<CheckBox>(R.id.check_hol)
            .also { it.isChecked = note.secret }
        val checkLock = editDialog.pView.findViewById<CheckBox>(R.id.check_lock)
            .also { it.isChecked = note.locked }

        val pColorPicker = editDialog.pView.findViewById<RadioGroup>(R.id.color_picker).also {
            it.setOnCheckedChangeListener { _, i ->
                val color = getColor(COLORS[RBIDS.indexOf(i)])
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

    private fun deleteNoteDialog(note: Note) {
        if (requestPermission()) return
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
        if (requestPermission()) return
        val showNoteDialog = Dialog(R.layout.popup_show_layout, note, note.title)
        showNoteDialog.pNeg.setOnClickListener {
            editNotePopup(note)
            showNoteDialog.dismiss()
        }
        showNoteDialog.pPos.setOnClickListener {
            deleteNoteDialog(note)
            showNoteDialog.dismiss()
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
            closePanelAndUnlock()
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


    /** #### Utils #### **/

    private fun requestPermission(): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, R.string.permission_toast, Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivityAndCollapse(intent)
            return true
        }
        return false
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
        intent.putExtra(EXTRA_NOTE, note)
        return PendingIntent.getBroadcast(
            context,
            note.id,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @SuppressLint("WrongConstant")
    private fun closePanelAndUnlock() {
        //unlock phone if locked
        if (isLocked) unlockAndRun(null)

        //close panel, won't work for A12+
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

        //mService?.onStartActivity(mTileToken)
        /*val intent = Intent(this, QSTile::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(intent)*/

    }

    /** #### App update #### **/

    private fun checkForUpdate() {
        FirebaseDatabase.getInstance().reference.child(context.getString(R.string.firebase_childName))
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val hashMap = HashMap<String?, String?>()
                        for (child in snapshot.children) hashMap[child.key] = child.value.toString()

                        if ((hashMap[context.getString(R.string.firebase_versionCode)]?.toInt()
                                ?: 0) > context.packageManager.getPackageInfo(
                                context.packageName,
                                0
                            ).versionCode
                        ) showUpdateNoti(
                            hashMap[context.getString(R.string.firebase_apk)]!!,
                            hashMap[context.getString(R.string.firebase_versionName)]!!
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.e("checkForUpdate", e.message)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("checkForUpdate", error.message)
                }
            })
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun showUpdateNoti(url: String, versionName: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            -2,
            Intent(ACTION_APP_UPDATE).also {
                it.putExtra(EXTRA_UPDATE_URL, url).putExtra(EXTRA_UPDATE_VNM, versionName)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        NotificationManagerCompat.from(this)
            .notify(
                -2, NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                    .setSmallIcon(R.drawable.ic_note)
                    .setContentTitle(getString(R.string.update_available))
                    .setContentText(getString(R.string.update_available_desc, versionName))
                    .addAction(
                        R.drawable.ic_download,
                        getString(R.string.download),
                        pendingIntent
                    )
                    .setContentIntent(pendingIntent)
                    .build()
            )
    }

    private fun installUpdate(url: String, versionName: String) {
        val destination = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            .toString() + "/" + context.getString(R.string.app_name) + "_" + versionName + ".apk"

        val file = File(destination)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(url))
        request.setMimeType("application/vnd.android.package-archive")
        request.setTitle(context.getString(R.string.app_name).toString() + " Update")
        request.setDescription(versionName)
        request.setDestinationUri(Uri.parse("file://$destination"))

        val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val apkFileUri = FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".provider",
                    File(destination)
                )
                val install = Intent(Intent.ACTION_VIEW)
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                install.setDataAndType(apkFileUri, "application/vnd.android.package-archive")
                startActivityAndCollapse(install)
                context.unregisterReceiver(this)
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        (context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }


    /** #### Detail view (samsung only) #### **/

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
                getPendingIntent(note, ACTION_EDIT_DIALOG)
            )
            noteView.setOnClickPendingIntent(
                R.id.qs_list_item_delete,
                getPendingIntent(note, ACTION_DELETE_DIALOG)
            )
            adapter.addView(noteView)
        }

        remoteViews.setOnClickPendingIntent(
            R.id.qs_detail_add,
            getPendingIntent(Note(generateNewNoteID()), ACTION_EDIT_DIALOG)
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