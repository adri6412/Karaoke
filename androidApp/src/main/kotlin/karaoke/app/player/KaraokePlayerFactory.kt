package karaoke.app.player

import android.content.Context
import karaoke.app.data.Song
import karaoke.app.data.SongFormat
import karaoke.shared.krz.Krz
import karaoke.shared.lyrics.KarLyrics
import karaoke.shared.model.AudioType
import karaoke.shared.model.HighlightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Costruisce il [KaraokePlayer] adatto al formato del brano. */
object KaraokePlayerFactory {

    suspend fun create(context: Context, song: Song, scope: CoroutineScope): KaraokePlayer {
        val file = File(song.filePath)
        return when (song.format) {
            SongFormat.MP4 ->
                withContext(Dispatchers.Main) { VideoKaraokePlayer(context, file, scope) }

            SongFormat.MIDI, SongFormat.KAR -> {
                val lyrics = withContext(Dispatchers.IO) {
                    runCatching { KarLyrics.fromMidiBytes(file.readBytes()) }.getOrNull()
                }
                // MIDI/KAR vanno sintetizzati: solo MediaPlayer (Sonivox) li riproduce.
                withContext(Dispatchers.Main) {
                    MidiKaraokePlayer(file, lyrics, HighlightMode.WORD, scope)
                }
            }

            SongFormat.KRZ -> {
                val extracted = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "krz_${song.id}")
                    Krz.extractTo(file, dir)
                }
                withContext(Dispatchers.Main) {
                    when (extracted.manifest.audioType) {
                        AudioType.VIDEO ->
                            VideoKaraokePlayer(context, extracted.mediaFile, scope)
                        // MIDI nel .krz: serve il sintetizzatore di MediaPlayer.
                        AudioType.MIDI ->
                            MidiKaraokePlayer(
                                extracted.mediaFile,
                                extracted.lyrics,
                                extracted.manifest.highlightMode,
                                scope
                            )
                        // MP3: ExoPlayer, posizione accurata per la sincronia del testo.
                        AudioType.MP3 ->
                            AudioKaraokePlayer(
                                context,
                                extracted.mediaFile,
                                extracted.lyrics,
                                extracted.manifest.highlightMode,
                                scope
                            )
                    }
                }
            }
        }
    }
}
