package karaoke.author.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Dialoghi nativi di apertura/salvataggio file (AWT). */
object Dialogs {
    fun openFile(title: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        return dialog.file?.let { File(dialog.directory, it) }
    }

    fun saveFile(title: String, defaultName: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        return dialog.file?.let { File(dialog.directory, it) }
    }
}
