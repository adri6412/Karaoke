package karaoke.author

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import karaoke.shared.krz.Krz
import karaoke.shared.model.AudioType
import karaoke.shared.model.KrzManifest
import karaoke.shared.model.LyricsDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stato del tab "Video → .krz": importa un video e lo reencoda con ffmpeg a max 720p
 * (video H.264) mantenendo l'audio invariato (`-c:a copy`), poi lo impacchetta in .krz.
 */
class VideoConvertState {
    var sourceFile by mutableStateOf<File?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var ffmpegPath by mutableStateOf(detectFfmpeg())
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var status by mutableStateOf(
        if (detectFfmpeg() != null) "ffmpeg trovato nel PATH. Seleziona un video da convertire."
        else "ffmpeg non trovato nel PATH: indica il percorso dell'eseguibile."
    )

    fun pickSource(file: File) {
        sourceFile = file
        if (title.isBlank()) title = file.nameWithoutExtension
        status = "Video selezionato: ${file.name}"
    }

    fun setFfmpeg(file: File) {
        ffmpegPath = file.absolutePath
        status = "ffmpeg: ${file.absolutePath}"
    }

    suspend fun convertAndExport(dest: File) {
        val src = sourceFile
        val ff = ffmpegPath
        when {
            src == null -> { status = "Seleziona prima un video."; return }
            ff == null -> { status = "ffmpeg non disponibile: indicane il percorso."; return }
        }
        busy = true
        progress = 0f
        status = "Conversione in corso…"
        try {
            withContext(Dispatchers.IO) {
                val target = if (dest.extension.equals("krz", true)) dest
                else File(dest.parentFile, dest.name + ".krz")
                val tmp = File.createTempFile("krzvid_", ".mp4").apply { deleteOnExit() }
                val durationSec = probeDuration(ff!!, src!!)
                runFfmpeg(ff, src, tmp, durationSec)
                val manifest = KrzManifest(
                    title = title.ifBlank { src.nameWithoutExtension },
                    artist = artist,
                    audioFile = "video.mp4",
                    audioType = AudioType.VIDEO,
                    durationMs = (durationSec * 1000).toLong()
                )
                Krz.writeStreaming(target, manifest, LyricsDoc(), tmp)
                tmp.delete()
                status = "Esportato: ${target.absolutePath}"
            }
            progress = 1f
        } catch (e: Exception) {
            status = "Errore conversione: ${e.message}"
        } finally {
            busy = false
        }
    }

    private fun ffprobePath(ffmpeg: String): String {
        val f = File(ffmpeg)
        val parent = f.parentFile
        if (parent != null) {
            val probe = File(parent, if (ffmpeg.endsWith(".exe", true)) "ffprobe.exe" else "ffprobe")
            if (probe.exists()) return probe.absolutePath
        }
        return "ffprobe"
    }

    private fun probeDuration(ffmpeg: String, src: File): Double = try {
        val process = ProcessBuilder(
            ffprobePath(ffmpeg), "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            src.absolutePath
        ).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        out.lineSequence().mapNotNull { it.trim().toDoubleOrNull() }.firstOrNull() ?: 0.0
    } catch (e: Exception) {
        0.0
    }

    private fun runFfmpeg(ffmpeg: String, src: File, out: File, durationSec: Double) {
        val command = listOf(
            ffmpeg, "-y", "-i", src.absolutePath,
            // Downscale solo se più alto di 720p, larghezza pari, niente upscaling.
            "-vf", "scale=-2:min(720\\,ih)",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
            "-c:a", "copy",                 // audio invariato
            "-movflags", "+faststart",
            out.absolutePath
        )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val timeRegex = Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")
        process.inputStream.bufferedReader().forEachLine { line ->
            if (durationSec > 0) {
                timeRegex.find(line)?.let { m ->
                    val cur = m.groupValues[1].toDouble() * 3600 +
                        m.groupValues[2].toDouble() * 60 +
                        m.groupValues[3].toDouble()
                    progress = (cur / durationSec).toFloat().coerceIn(0f, 1f)
                }
            }
        }
        val code = process.waitFor()
        if (code != 0) throw RuntimeException("ffmpeg terminato con codice $code")
    }

    companion object {
        fun detectFfmpeg(): String? = try {
            val process = ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start()
            process.inputStream.readBytes()
            if (process.waitFor() == 0) "ffmpeg" else null
        } catch (e: Exception) {
            null
        }
    }
}
