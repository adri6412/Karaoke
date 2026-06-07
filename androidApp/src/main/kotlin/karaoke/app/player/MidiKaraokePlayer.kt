package karaoke.app.player

import android.media.AudioAttributes
import android.media.MediaPlayer
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
 * Player per MIDI/KAR basato su [MediaPlayer], che integra il sintetizzatore
 * Sonivox di Android (ExoPlayer non sintetizza i MIDI). Per gli MP3 si usa invece
 * [AudioKaraokePlayer] (ExoPlayer), più preciso sulla posizione. Il testo viene
 * allineato facendo polling su `currentPosition`.
 */
class MidiKaraokePlayer(
    file: File,
    override val lyrics: LyricsDoc?,
    override val highlightMode: HighlightMode,
    private val scope: CoroutineScope
) : KaraokePlayer {

    override val isVideo = false

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val player = MediaPlayer()
    private var tickJob: Job? = null
    private var prepared = false
    private var playWhenReady = false

    init {
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { mp ->
                prepared = true
                _state.update { it.copy(durationMs = mp.duration.toLong().coerceAtLeast(0)) }
                if (playWhenReady) { playWhenReady = false; play() }
            }
            player.setOnCompletionListener {
                _state.update { it.copy(isPlaying = false, ended = true, positionMs = it.durationMs) }
                stopTicker()
            }
            player.setOnErrorListener { _, what, extra ->
                _state.update { it.copy(error = "Errore riproduzione (code $what/$extra)", isPlaying = false) }
                stopTicker()
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Impossibile aprire l'audio") }
        }
    }

    override fun play() {
        if (!prepared) { playWhenReady = true; return }
        if (_state.value.ended) {
            player.seekTo(0)
            _state.update { it.copy(ended = false, positionMs = 0) }
        }
        player.start()
        _state.update { it.copy(isPlaying = true, ended = false) }
        startTicker()
    }

    override fun pause() {
        if (prepared && player.isPlaying) {
            player.pause()
            _state.update { it.copy(isPlaying = false) }
            stopTicker()
        }
    }

    override fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        if (prepared) {
            player.seekTo(positionMs.toInt())
            _state.update { it.copy(positionMs = positionMs, ended = false) }
        }
    }

    override fun release() {
        stopTicker()
        runCatching { player.release() }
    }

    private fun startTicker() {
        stopTicker()
        tickJob = scope.launch {
            while (isActive) {
                if (prepared && player.isPlaying) {
                    _state.update { it.copy(positionMs = player.currentPosition.toLong()) }
                }
                delay(40)
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }
}
