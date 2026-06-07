package karaoke.author.audio

import karaoke.shared.model.AudioType
import java.io.File
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** Motore di riproduzione per l'anteprima/sincronizzazione nel tool di authoring. */
interface AudioEngine {
    val durationMs: Long
    val isPlaying: Boolean
    fun play()
    fun pause()
    fun stop()
    fun seekTo(ms: Long)
    fun positionMs(): Long
    fun release()
}

object AudioEngineFactory {
    fun create(file: File, type: AudioType): AudioEngine = when (type) {
        AudioType.MIDI -> MidiAudioEngine(file)
        AudioType.MP3 -> ClipAudioEngine(file)
        AudioType.VIDEO -> throw IllegalArgumentException("Il video si converte nel tab 'Video → .krz', non qui.")
    }
}

/** Riproduce MIDI/KAR con il Sequencer di sistema (sintetizzatore di default della JVM). */
private class MidiAudioEngine(file: File) : AudioEngine {
    private val sequencer: Sequencer = MidiSystem.getSequencer().apply {
        open()
        sequence = MidiSystem.getSequence(file)
    }

    override val durationMs: Long get() = sequencer.microsecondLength / 1000
    override val isPlaying: Boolean get() = sequencer.isRunning
    override fun play() { sequencer.start() }
    override fun pause() { sequencer.stop() }
    override fun stop() { sequencer.stop(); sequencer.microsecondPosition = 0 }
    override fun seekTo(ms: Long) {
        sequencer.microsecondPosition = (ms * 1000).coerceIn(0, sequencer.microsecondLength)
    }
    override fun positionMs(): Long = sequencer.microsecondPosition / 1000
    override fun release() { runCatching { sequencer.stop(); sequencer.close() } }
}

/** Riproduce MP3 (decodificato via mp3spi) caricandolo in un Clip PCM in memoria. */
private class ClipAudioEngine(file: File) : AudioEngine {
    private val clip: Clip

    init {
        val encoded = AudioSystem.getAudioInputStream(file)
        val base = encoded.format
        val decodedFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            base.sampleRate,
            16,
            base.channels,
            base.channels * 2,
            base.sampleRate,
            false
        )
        val decoded = AudioSystem.getAudioInputStream(decodedFormat, encoded)
        clip = AudioSystem.getClip().apply { open(decoded) }
        decoded.close()
        encoded.close()
    }

    override val durationMs: Long get() = clip.microsecondLength / 1000
    override val isPlaying: Boolean get() = clip.isRunning
    override fun play() { clip.start() }
    override fun pause() { clip.stop() }
    override fun stop() { clip.stop(); clip.microsecondPosition = 0 }
    override fun seekTo(ms: Long) {
        clip.microsecondPosition = (ms * 1000).coerceIn(0, clip.microsecondLength)
    }
    override fun positionMs(): Long = clip.microsecondPosition / 1000
    override fun release() { runCatching { clip.stop(); clip.close() } }
}
