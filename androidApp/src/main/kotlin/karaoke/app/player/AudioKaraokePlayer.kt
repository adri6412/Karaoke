package karaoke.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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

/**
 * Player audio basato su ExoPlayer (media3). Gestisce MIDI/KAR e MP3 (audio dei
 * pacchetti .krz). Usa ExoPlayer — e non MediaPlayer — perché `currentPosition`
 * di MediaPlayer è inaffidabile su molti MP3 (salta o si blocca), facendo perdere
 * la sincronia col testo; ExoPlayer riporta una posizione accurata. Il testo viene
 * allineato facendo polling su `currentPosition`.
 */
class AudioKaraokePlayer(
    context: Context,
    file: File,
    override val lyrics: LyricsDoc?,
    override val highlightMode: HighlightMode,
    private val scope: CoroutineScope
) : KaraokePlayer {

    override val isVideo = false

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var tickJob: Job? = null

    init {
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY ->
                            _state.update { it.copy(durationMs = player.duration.coerceAtLeast(0)) }
                        Player.STATE_ENDED -> {
                            _state.update { it.copy(isPlaying = false, ended = true, positionMs = it.durationMs) }
                            stopTicker()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying, ended = if (isPlaying) false else it.ended) }
                    if (isPlaying) startTicker() else stopTicker()
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    if (error != null) {
                        _state.update { it.copy(error = error.message ?: "Errore riproduzione", isPlaying = false) }
                        stopTicker()
                    }
                }
            })
            player.prepare()
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Impossibile aprire l'audio") }
        }
    }

    override fun play() {
        if (_state.value.ended) {
            player.seekTo(0)
            _state.update { it.copy(ended = false, positionMs = 0) }
        }
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        _state.update { it.copy(positionMs = positionMs, ended = false) }
    }

    override fun release() {
        stopTicker()
        runCatching { player.release() }
    }

    private fun startTicker() {
        stopTicker()
        tickJob = scope.launch {
            while (isActive) {
                _state.update { it.copy(positionMs = player.currentPosition.coerceAtLeast(0)) }
                delay(40)
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }
}
