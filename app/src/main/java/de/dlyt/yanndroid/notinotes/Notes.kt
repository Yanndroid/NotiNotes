package de.dlyt.yanndroid.notinotes

import android.content.Context
import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.util.stream.Collectors

class Notes {

    class Note(id: Int = -1) :
        Serializable {
        var title: String? = null
        var content: String? = null
        var color: Int = Color.GRAY
        var id: Int = -1
        var secret: Boolean = false
        var locked: Boolean = true

        init {
            this.id = id
        }
    }

    companion object {

        fun getNotes(context: Context): ArrayList<Note> = Gson().fromJson(
            context.getSharedPreferences("sp", Context.MODE_PRIVATE).getString("notes", "[]"),
            object : TypeToken<ArrayList<Note>>() {}.type
        )

        private fun saveNotes(context: Context, list: ArrayList<Note>) {
            context.getSharedPreferences("sp", Context.MODE_PRIVATE).edit()
                .putString("notes", Gson().toJson(list)).apply()
        }

        fun saveNote(context: Context, note: Note) {
            val list = getNotes(context)
            if (note.id == -1) note.id = generateNewNoteID(list)
            for (i in list.indices) {
                if (note.id == list[i].id) {
                    list[i] = note
                    saveNotes(context, list)
                    Notification.show(context, note)
                    return
                }
            }
            list.add(note)
            saveNotes(context, list)
            Notification.show(context, note)
        }

        fun deleteNote(context: Context, note: Note) {
            val list = getNotes(context)
            for (i in list.indices) {
                if (note.id == list[i].id) {
                    list.removeAt(i)
                    saveNotes(context, list)
                    Notification.cancel(context, note)
                    return
                }
            }
        }

        fun generateNewNoteID(context: Context): Int = generateNewNoteID(getNotes(context))

        private fun generateNewNoteID(list: ArrayList<Note>): Int {
            val takenIDs = list.stream().map { note -> note.id }.collect(Collectors.toList())
            var newID = 0
            while (takenIDs.contains(newID)) newID++
            return newID
        }
    }

}