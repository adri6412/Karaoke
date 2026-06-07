package karaoke.shared.krz

import karaoke.shared.model.AudioType
import karaoke.shared.model.KrzManifest
import karaoke.shared.model.LyricLine
import karaoke.shared.model.LyricWord
import karaoke.shared.model.LyricsDoc
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KrzRoundTripTest {

    @Test
    fun writeThenReadPreservesContent() {
        val lyrics = LyricsDoc(
            listOf(
                LyricLine(
                    startMs = 0, endMs = 1000,
                    words = listOf(
                        LyricWord("Ciao ", 0, 500),
                        LyricWord("mondo", 500, 1000)
                    )
                )
            )
        )
        val manifest = KrzManifest(
            title = "Test Song",
            artist = "Tester",
            audioFile = "audio.mp3",
            audioType = AudioType.MP3,
            durationMs = 1000
        )
        val audio = byteArrayOf(1, 2, 3, 4, 5)
        val pkg = KrzPackage(manifest, lyrics, audio)

        val out = ByteArrayOutputStream()
        Krz.write(out, pkg)
        val restored = Krz.read(ByteArrayInputStream(out.toByteArray()))

        assertEquals(manifest, restored.manifest)
        assertEquals(lyrics, restored.lyrics)
        assertContentEquals(audio, restored.audioBytes)
    }
}
