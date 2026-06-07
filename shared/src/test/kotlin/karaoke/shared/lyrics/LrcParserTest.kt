package karaoke.shared.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcParserTest {

    @Test
    fun parsesStandardLrcWithMetadataAndDistributesWords() {
        val lrc = """
            [ti:Prova]
            [ar:Io]
            [00:00.00]Ciao mondo
            [00:02.00]Seconda riga
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals("Prova", result.title)
        assertEquals("Io", result.artist)
        // riga 0: "Ciao" @0, "mondo" @1000 (distribuito su 0..2000)
        val line0 = result.words.filter { it.lineIndex == 0 }
        assertEquals(listOf("Ciao", "mondo"), line0.map { it.text.trim() })
        assertEquals(0L, line0[0].startMs)
        assertEquals(1000L, line0[1].startMs)
        // riga 1 comincia a 2000
        assertEquals(2000L, result.words.first { it.lineIndex == 1 }.startMs)
    }

    @Test
    fun parsesEnhancedLrcWithPerWordTimings() {
        val lrc = "[00:01.00]<00:01.00>Twin<00:01.50>kle<00:02.00>twinkle"
        val result = LrcParser.parse(lrc)

        assertEquals(listOf("Twin", "kle", "twinkle"), result.words.map { it.text })
        assertEquals(1000L, result.words[0].startMs)
        assertEquals(1500L, result.words[1].startMs)
        assertEquals(2000L, result.words[2].startMs)
    }

    @Test
    fun appliesOffset() {
        val lrc = """
            [offset:+500]
            [00:02.00]uno due
        """.trimIndent()
        val result = LrcParser.parse(lrc)
        // 2000 - 500 = 1500
        assertEquals(1500L, result.words.first().startMs)
    }
}
