package karaoke.author

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import karaoke.author.audio.AudioEngine
import karaoke.author.audio.AudioEngineFactory
import karaoke.shared.krz.Krz
import karaoke.shared.krz.KrzPackage
import karaoke.shared.lyrics.LrcParser
import karaoke.shared.lyrics.LyricsAssembler
import karaoke.shared.model.AudioType
import karaoke.shared.model.HighlightMode
import karaoke.shared.model.KrzManifest
import karaoke.shared.model.LyricsDoc
import java.io.File

/** Una parola dell'editor con il suo tempo d'inizio (assegnato col tap-sync). */
class WordTiming(val text: String, val lineIndex: Int, startMs: Long?) {
    var startMs by mutableStateOf(startMs)
}

/** Stato e logica del tool di authoring (osservabile da Compose). */
class AuthorState {
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var lyricsText by mutableStateOf("")

    var highlightMode by mutableStateOf(HighlightMode.WORD)

    var audioFile by mutableStateOf<File?>(null)
    var audioType by mutableStateOf<AudioType?>(null)
    var durationMs by mutableStateOf(0L)
    var positionMs by mutableStateOf(0L)
    var isPlaying by mutableStateOf(false)

    val words = mutableStateListOf<WordTiming>()
    var nextTapIndex by mutableStateOf(0)

    var status by mutableStateOf("Carica un audio (MIDI/MP3) e inserisci il testo.")

    private var engine: AudioEngine? = null

    fun loadAudio(file: File) {
        val type = audioTypeForExtension(file.extension.lowercase())
        if (type == null) {
            status = "Formato audio non supportato: .${file.extension}"
            return
        }
        try {
            engine?.release()
            val newEngine = AudioEngineFactory.create(file, type)
            engine = newEngine
            audioFile = file
            audioType = type
            durationMs = newEngine.durationMs
            positionMs = 0
            isPlaying = false
            if (title.isBlank()) title = file.nameWithoutExtension
            status = "Audio caricato: ${file.name} (${type.name})"
        } catch (e: Exception) {
            status = "Errore nel caricamento audio: ${e.message}"
        }
    }

    fun applyLyrics() {
        words.clear()
        nextTapIndex = 0
        var lineIndex = 0
        lyricsText.split('\n').forEach { rawLine ->
            val tokens = rawLine.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
                tokens.forEach { words.add(WordTiming("$it ", lineIndex, null)) }
                lineIndex++
            }
        }
        highlightMode = HighlightMode.WORD  // testo manuale: si sincronizza per parola col tap
        status = if (words.isEmpty()) "Inserisci del testo da sincronizzare."
        else "${words.size} parole su $lineIndex righe. Premi Play e usa TAP per sincronizzare."
    }

    /** Importa testo già temporizzato da un file .lrc (standard o enhanced). */
    fun importLrc(file: File) {
        try {
            val result = LrcParser.parse(file.readText(Charsets.UTF_8))
            if (result.words.isEmpty()) {
                status = "LRC senza righe temporizzate."
                return
            }
            words.clear()
            result.words.forEach { words.add(WordTiming(it.text, it.lineIndex, it.startMs)) }
            nextTapIndex = words.size  // tempi già presenti
            // LRC per-riga → evidenziazione riga-per-riga (rispetta i timecode senza inventare
            // tempi di parola); LRC enhanced → parola-per-parola.
            highlightMode = if (result.wordLevel) HighlightMode.WORD else HighlightMode.LINE
            lyricsText = result.words
                .groupBy { it.lineIndex }
                .toSortedMap()
                .values
                .joinToString("\n") { line -> line.joinToString("") { it.text }.trim() }
            result.title?.let { if (title.isBlank()) title = it }
            result.artist?.let { if (artist.isBlank()) artist = it }
            val lineCount = (result.words.maxOfOrNull { it.lineIndex } ?: -1) + 1
            status = "LRC importato: ${words.size} parole su $lineCount righe (tempi impostati)."
        } catch (e: Exception) {
            status = "Errore import LRC: ${e.message}"
        }
    }

    fun play() {
        val e = engine ?: run { status = "Carica prima un audio."; return }
        e.play(); isPlaying = true
    }

    fun pause() { engine?.pause(); isPlaying = false }

    fun togglePlay() { if (isPlaying) pause() else play() }

    fun stop() {
        engine?.stop(); isPlaying = false; positionMs = 0
    }

    fun seekTo(ms: Long) {
        engine?.seekTo(ms); positionMs = ms
    }

    /** Aggiorna la posizione dall'engine (chiamata dal ticker UI). */
    fun refreshPosition() {
        val e = engine ?: return
        positionMs = e.positionMs()
        if (isPlaying && !e.isPlaying) isPlaying = false // arrivato alla fine
    }

    /** Assegna alla prossima parola il tempo corrente e avanza (tap-sync). */
    fun tapNext() {
        val pos = engine?.positionMs() ?: positionMs
        if (nextTapIndex in words.indices) {
            words[nextTapIndex].startMs = pos
            nextTapIndex++
        }
    }

    fun setWordTime(index: Int, ms: Long?) {
        if (index in words.indices) words[index].startMs = ms
    }

    fun resetTiming() {
        words.forEach { it.startMs = null }
        nextTapIndex = 0
        status = "Tempi azzerati."
    }

    fun buildLyricsDoc(): LyricsDoc =
        LyricsAssembler.assemble(words.map { LyricsAssembler.WordInput(it.text, it.lineIndex, it.startMs) })

    fun export(dest: File) {
        val file = audioFile
        val type = audioType
        if (file == null || type == null) { status = "Carica prima un audio."; return }
        if (words.isEmpty()) { status = "Inserisci e sincronizza il testo prima di esportare."; return }
        try {
            val audioEntry = if (type == AudioType.MP3) "audio.mp3" else "song.mid"
            val manifest = KrzManifest(
                title = title.ifBlank { file.nameWithoutExtension },
                artist = artist,
                audioFile = audioEntry,
                audioType = type,
                highlightMode = highlightMode,
                durationMs = durationMs
            )
            val pkg = KrzPackage(manifest, buildLyricsDoc(), file.readBytes())
            val target = if (dest.extension.equals("krz", true)) dest
            else File(dest.parentFile, dest.name + ".krz")
            Krz.write(target, pkg)
            status = "Esportato: ${target.absolutePath}"
        } catch (e: Exception) {
            status = "Errore in esportazione: ${e.message}"
        }
    }

    fun release() { engine?.release(); engine = null }

    private fun audioTypeForExtension(ext: String): AudioType? = when (ext) {
        "mid", "midi", "kar" -> AudioType.MIDI
        "mp3" -> AudioType.MP3
        else -> null
    }
}
