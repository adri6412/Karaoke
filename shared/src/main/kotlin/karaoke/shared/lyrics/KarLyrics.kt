package karaoke.shared.lyrics

import karaoke.shared.midi.ParsedMidi
import karaoke.shared.midi.SmfParser
import karaoke.shared.model.LyricLine
import karaoke.shared.model.LyricWord
import karaoke.shared.model.LyricsDoc

/**
 * Estrae testo karaoke temporizzato da file MIDI/KAR.
 *
 * Convenzione Soft-Karaoke: i meta-eventi Lyric (0x05) — o, in mancanza, Text (0x01) —
 * contengono sillabe/parole; `/` o `\` indicano l'inizio di una nuova riga e `@...`
 * sono righe di metadati da ignorare.
 */
object KarLyrics {

    private const val LAST_WORD_TAIL_MS = 600L
    private const val MAX_WORD_MS = 4000L

    fun fromMidiBytes(bytes: ByteArray): LyricsDoc = fromParsed(SmfParser.parse(bytes))

    fun fromParsed(parsed: ParsedMidi): LyricsDoc {
        val lyricEvents = parsed.textEvents.filter { it.type == 0x05 }
        val source = (if (lyricEvents.isNotEmpty()) lyricEvents
        else parsed.textEvents.filter { it.type == 0x01 })
            .sortedBy { it.tick }

        // 1) Raggruppa i token in righe rispettando i marcatori di a capo.
        val lines = ArrayList<ArrayList<Pair<String, Long>>>()   // (testo, tick)
        var current = ArrayList<Pair<String, Long>>()
        fun flush() { if (current.isNotEmpty()) { lines.add(current); current = ArrayList() } }

        for (ev in source) {
            var t = ev.text.replace("\r", "")
            if (t.isEmpty() || t.startsWith("@")) continue
            var breakLine = false
            while (t.isNotEmpty() && (t[0] == '/' || t[0] == '\\' || t[0] == '\n')) {
                breakLine = true
                t = t.substring(1)
            }
            if (breakLine) flush()
            if (t.isEmpty()) continue
            t.split('\n').forEachIndexed { i, part ->
                if (i > 0) flush()
                if (part.isNotEmpty()) current.add(part to ev.tick)
            }
        }
        flush()

        // 2) Assegna i tempi: la fine di una parola è l'inizio della successiva (con limite).
        val flatStarts = ArrayList<Long>()
        val startsByLine = lines.map { line ->
            line.map { parsed.tickToMs(it.second).also { ms -> flatStarts.add(ms) } }
        }

        var globalIndex = 0
        val result = ArrayList<LyricLine>(lines.size)
        for (li in lines.indices) {
            val words = ArrayList<LyricWord>(lines[li].size)
            for (wi in lines[li].indices) {
                val start = startsByLine[li][wi]
                val nextStart =
                    if (globalIndex + 1 < flatStarts.size) flatStarts[globalIndex + 1]
                    else start + LAST_WORD_TAIL_MS
                val end = minOf(maxOf(nextStart, start), start + MAX_WORD_MS)
                words.add(LyricWord(lines[li][wi].first, start, end))
                globalIndex++
            }
            if (words.isNotEmpty()) {
                result.add(LyricLine(words.first().startMs, words.last().endMs, words))
            }
        }
        return LyricsDoc(result)
    }
}
