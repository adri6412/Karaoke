package karaoke.shared.lyrics

import karaoke.shared.model.LyricLine
import karaoke.shared.model.LyricWord
import karaoke.shared.model.LyricsDoc

/**
 * Costruisce un [LyricsDoc] a partire da parole con tempo d'inizio (eventualmente
 * mancante) raggruppate per riga. Usato dal tool di authoring desktop dopo il
 * tap-sync. La fine di una parola è l'inizio della successiva (con un limite).
 */
object LyricsAssembler {

    data class WordInput(val text: String, val lineIndex: Int, val startMs: Long?)

    fun assemble(
        words: List<WordInput>,
        lastTailMs: Long = 600L,
        maxWordMs: Long = 6_000L
    ): LyricsDoc {
        if (words.isEmpty()) return LyricsDoc()

        // 1) Riempi i tempi mancanti propagando l'ultimo noto; forza non-decrescenza.
        val starts = LongArray(words.size)
        var last = 0L
        for (i in words.indices) {
            last = words[i].startMs ?: last
            starts[i] = last
        }
        for (i in 1 until starts.size) {
            if (starts[i] < starts[i - 1]) starts[i] = starts[i - 1]
        }

        // 2) Raggruppa per riga mantenendo l'ordine; assegna le fini.
        val result = ArrayList<LyricLine>()
        var i = 0
        while (i < words.size) {
            val lineIndex = words[i].lineIndex
            val lineWords = ArrayList<LyricWord>()
            while (i < words.size && words[i].lineIndex == lineIndex) {
                val start = starts[i]
                val rawEnd = if (i + 1 < words.size) starts[i + 1] else start + lastTailMs
                val end = minOf(maxOf(rawEnd, start), start + maxWordMs)
                lineWords.add(LyricWord(words[i].text, start, end))
                i++
            }
            if (lineWords.isNotEmpty()) {
                result.add(LyricLine(lineWords.first().startMs, lineWords.last().endMs, lineWords))
            }
        }
        return LyricsDoc(result)
    }
}
