package karaoke.shared.lyrics

import karaoke.shared.lyrics.LyricsAssembler.WordInput
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsAssemblerTest {

    @Test
    fun groupsWordsByLineAndChainsTimings() {
        val doc = LyricsAssembler.assemble(
            listOf(
                WordInput("Tanti", 0, 0),
                WordInput("auguri", 0, 500),
                WordInput("a", 1, 1000),
                WordInput("te", 1, 1500)
            )
        )

        assertEquals(2, doc.lines.size)
        assertEquals(2, doc.lines[0].words.size)
        // la fine di una parola = inizio della successiva
        assertEquals(0L, doc.lines[0].words[0].startMs)
        assertEquals(500L, doc.lines[0].words[0].endMs)
        assertEquals(1000L, doc.lines[0].words[1].endMs)
        // riga 0: da 0 alla fine dell'ultima parola
        assertEquals(0L, doc.lines[0].startMs)
        assertEquals(1000L, doc.lines[0].endMs)
        // ultima parola in assoluto: tail di default
        assertEquals(1500L + 600L, doc.lines[1].words[1].endMs)
    }

    @Test
    fun fillsMissingTimingsByCarryingForward() {
        val doc = LyricsAssembler.assemble(
            listOf(
                WordInput("uno", 0, 1000),
                WordInput("due", 0, null),   // eredita 1000
                WordInput("tre", 0, 2000)
            )
        )
        assertEquals(1000L, doc.lines[0].words[0].startMs)
        assertEquals(1000L, doc.lines[0].words[1].startMs)
        assertEquals(2000L, doc.lines[0].words[2].startMs)
    }
}
