package de.dlyt.yanndroid.notinotes

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

class Dialog(private val context: Context, layoutRes: Int, note: Notes.Note, title: String?) {
    private val windowManager: WindowManager
    val pView: View
    val pTitle: TextView
    val pNote: TextView
    val pNeg: Button
    val pPos: Button
    val pIcon: ImageView

    init {
        closePanelAndUnlock()
        windowManager = context.getSystemService(TileService.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
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
            .also { it.setTextColor(context.getColor(Notes.COLORS[note.colorIndex])) }
        pPos = pView.findViewById<Button>(R.id.pPos)
            .also { it.setTextColor(context.getColor(Notes.COLORS[note.colorIndex])) }
        pIcon = pView.findViewById<ImageView>(R.id.pIcon)
            .also { it.setColorFilter(context.getColor(Notes.COLORS[note.colorIndex])) }
    }

    fun dismiss() = windowManager.removeView(pView)

    @SuppressLint("WrongConstant")
    fun closePanelAndUnlock() {
        //unlock phone if locked
        //if (isLocked) unlockAndRun(null)


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

    }
}