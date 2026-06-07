package karaoke.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import karaoke.shared.krz.Krz
import karaoke.shared.midi.ParsedMidi
import karaoke.shared.midi.SmfParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copia un file selezionato (SAF) nello storage app ed estrae i metadati. */
class SongImporter(private val context: Context) {

    private data class Meta(val title: String, val artist: String, val durationMs: Long)

    suspend fun import(uri: Uri): Song = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri) ?: "canzone"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val format = formatForExtension(ext)
            ?: throw IllegalArgumentException("Formato non supportato: .$ext")

        val songsDir = File(context.filesDir, "songs").apply { mkdirs() }
        val dest = File(songsDir, "${UUID.randomUUID()}.${ext.ifEmpty { "dat" }}")
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("Impossibile leggere il file selezionato")

        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Brano" }
        val meta = runCatching { extractMetadata(dest, format, fallbackTitle) }
            .getOrDefault(Meta(fallbackTitle, "", 0L))

        Song(
            title = meta.title,
            artist = meta.artist,
            format = format,
            filePath = dest.absolutePath,
            durationMs = meta.durationMs
        )
    }

    private fun extractMetadata(file: File, format: SongFormat, fallbackTitle: String): Meta =
        when (format) {
            SongFormat.KRZ -> {
                val m = Krz.readManifest(file)
                Meta(m.title.ifBlank { fallbackTitle }, m.artist, m.durationMs)
            }
            SongFormat.MP4 -> {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.absolutePath)
                    val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    Meta(title?.ifBlank { null } ?: fallbackTitle, artist ?: "", duration)
                } finally {
                    runCatching { r.release() }
                }
            }
            SongFormat.MIDI, SongFormat.KAR -> {
                val parsed = SmfParser.parse(file.readBytes())
                Meta(midiTitle(parsed, fallbackTitle), "", parsed.durationMs)
            }
        }

    private fun midiTitle(parsed: ParsedMidi, fallback: String): String {
        parsed.textEvents.firstOrNull { it.text.trimStart().startsWith("@T") }?.let {
            val v = it.text.trim().removePrefix("@T").trim()
            if (v.isNotEmpty()) return v
        }
        parsed.textEvents.firstOrNull {
            it.type == 0x03 && it.text.isNotBlank() && !it.text.trimStart().startsWith("@")
        }?.let { return it.text.trim() }
        return fallback
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }

    private fun formatForExtension(ext: String): SongFormat? = when (ext) {
        "mid", "midi" -> SongFormat.MIDI
        "kar" -> SongFormat.KAR
        "mp4", "m4v" -> SongFormat.MP4
        "krz" -> SongFormat.KRZ
        else -> null
    }
}
