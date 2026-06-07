package karaoke.app.player

import karaoke.shared.model.HighlightMode
import karaoke.shared.model.LyricsDoc
import kotlinx.coroutines.flow.StateFlow

/** Stato di riproduzione osservabile dall'interfaccia. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val ended: Boolean = false,
    val error: String? = null
)

/** Astrazione comune per i diversi tipi di sorgente karaoke. */
interface KaraokePlayer {
    val state: StateFlow<PlaybackState>

    /** Testo temporizzato da mostrare; `null` per i video (testo già impresso). */
    val lyrics: LyricsDoc?

    /** Modalità di evidenziazione del testo (parola-per-parola o riga-per-riga). */
    val highlightMode: HighlightMode

    /** `true` se la sorgente è un video (MP4) da mostrare a schermo. */
    val isVideo: Boolean

    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun release()
}
