package karaoke.shared.midi

import karaoke.shared.lyrics.KarLyrics
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class SmfParserTest {

    @Test
    fun parsesTempoAndLyricsAndConvertsTicks() {
        val midi = buildMidi {
            setTempo(0, 500_000)              // 120 BPM
            lyric(0, "Ciao")
            lyric(480, "/mondo")             // un quarto dopo + nuova riga
            endOfTrack(0)
        }

        val parsed = SmfParser.parse(midi)

        assertEquals(480, parsed.division)
        assertEquals(2, parsed.textEvents.count { it.type == 0x05 })
        assertEquals(0L, parsed.tickToMs(0))
        assertEquals(500L, parsed.tickToMs(480))   // 500_000us / quarto, 480 ppq

        val doc = KarLyrics.fromParsed(parsed)
        assertEquals(2, doc.lines.size)            // "/mondo" apre una nuova riga
        assertEquals("Ciao", doc.lines[0].words[0].text)
        assertEquals("mondo", doc.lines[1].words[0].text)
        assertEquals(0L, doc.lines[0].words[0].startMs)
        assertEquals(500L, doc.lines[1].words[0].startMs)
    }
}

// --- helper per costruire uno Standard MIDI File in memoria -----------------------

private class TrackBuilder {
    val body = ByteArrayOutputStream()

    fun setTempo(delta: Int, microsPerQuarter: Int) {
        body.write(varLen(delta)); body.write(0xFF); body.write(0x51); body.write(3)
        body.write((microsPerQuarter shr 16) and 0xFF)
        body.write((microsPerQuarter shr 8) and 0xFF)
        body.write(microsPerQuarter and 0xFF)
    }

    fun lyric(delta: Int, text: String) = meta(delta, 0x05, text)

    fun meta(delta: Int, type: Int, text: String) {
        val data = text.toByteArray(Charsets.US_ASCII)
        body.write(varLen(delta)); body.write(0xFF); body.write(type)
        body.write(varLen(data.size)); body.write(data)
    }

    fun endOfTrack(delta: Int) {
        body.write(varLen(delta)); body.write(0xFF); body.write(0x2F); body.write(0x00)
    }
}

private fun buildMidi(division: Int = 480, build: TrackBuilder.() -> Unit): ByteArray {
    val track = TrackBuilder().apply(build).body.toByteArray()
    val out = ByteArrayOutputStream()
    out.write("MThd".toByteArray(Charsets.US_ASCII))
    out.write(int32(6)); out.write(int16(0)); out.write(int16(1)); out.write(int16(division))
    out.write("MTrk".toByteArray(Charsets.US_ASCII))
    out.write(int32(track.size)); out.write(track)
    return out.toByteArray()
}

private fun int16(v: Int) = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())

private fun int32(v: Int) = byteArrayOf(
    ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
    ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte()
)

private fun varLen(value: Int): ByteArray {
    val bytes = ArrayList<Byte>()
    var v = value
    bytes.add(0, (v and 0x7F).toByte())
    v = v shr 7
    while (v > 0) {
        bytes.add(0, ((v and 0x7F) or 0x80).toByte())
        v = v shr 7
    }
    return bytes.toByteArray()
}
