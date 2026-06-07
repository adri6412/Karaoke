package karaoke.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tipo di media principale contenuto in un pacchetto .krz. */
@Serializable
enum class AudioType {
    @SerialName("mp3") MP3,
    @SerialName("midi") MIDI,

    /** Video MP4 con testo già impresso (nessun testo temporizzato separato). */
    @SerialName("video") VIDEO
}

/** Modalità di evidenziazione del testo durante la riproduzione. */
@Serializable
enum class HighlightMode {
    @SerialName("word") WORD,
    @SerialName("line") LINE
}

/**
 * Metadati del pacchetto karaoke proprietario (`manifest.json` dentro il file .krz).
 */
@Serializable
data class KrzManifest(
    val formatVersion: Int = 1,
    val title: String,
    val artist: String = "",
    val audioFile: String,
    val audioType: AudioType,
    val lyricsFile: String = "lyrics.json",
    val highlightMode: HighlightMode = HighlightMode.WORD,
    val durationMs: Long = 0L,
    val cover: String? = null
)

/** Una singola unità di testo evidenziabile (parola o sillaba) con i suoi tempi. */
@Serializable
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

/** Una riga di testo, composta da parole/sillabe temporizzate. */
@Serializable
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val words: List<LyricWord>
) {
    /** Testo completo della riga (le parole portano già la propria spaziatura). */
    val text: String get() = words.joinToString("") { it.text }
}

/** Documento di testo temporizzato: comune a .krz e all'estrazione da MIDI/KAR. */
@Serializable
data class LyricsDoc(
    val lines: List<LyricLine> = emptyList()
)
