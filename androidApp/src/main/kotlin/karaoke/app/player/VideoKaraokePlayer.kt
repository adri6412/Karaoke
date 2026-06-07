package karaoke.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import karaoke.shared.model.HighlightMode
import karaoke.shared.model.LyricsDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Player video (MP4) basato su ExoPlayer. Il testo è già impresso nel video. */
class VideoKaraokePlayer(
    context: Context,
    file: File,
    private val scope: CoroutineScope
) : KaraokePlayer {

    override val isVideo = true
    override val lyrics: LyricsDoc? = null
    override val highlightMode: HighlightMode = HighlightMode.WORD

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private var tickJob: Job? = null

    init {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY ->
                        _state.update { it.copy(durationMs = exoPlayer.duration.coerceAtLeast(0)) }
                    Player.STATE_ENDED -> {
                        _state.update { it.copy(isPlaying = false, ended = true) }
                        stopTicker()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying, ended = if (isPlaying) false else it.ended) }
                if (isPlaying) startTicker() else stopTicker()
            }

            override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                if (error != null) _state.update { it.copy(error = error.message ?: "Errore video") }
            }
        })
        exoPlayer.prepare()
    }

    override fun play() { exoPlayer.play() }
    override fun pause() { exoPlayer.pause() }
    override fun togglePlayPause() { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
    override fun seekTo(positionMs: Long) { exoPlayer.seekTo(positionMs.coerceAtLeast(0)) }

    override fun release() {
        stopTicker()
        runCatching { exoPlayer.release() }
    }

    private fun startTicker() {
        stopTicker()
        tickJob = scope.launch {
            while (isActive) {
                _state.update { it.copy(positionMs = exoPlayer.currentPosition.coerceAtLeast(0)) }
                // Il testo è impresso nel video: la posizione serve solo allo slider,
                // 250ms (4Hz) bastano. Tick frequenti = ricomposizioni/GC inutili che
                // su hardware debole causano scatti nel video.
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }
}
