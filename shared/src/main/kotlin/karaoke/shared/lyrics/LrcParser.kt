package karaoke.shared.lyrics

import karaoke.shared.lyrics.LyricsAssembler.WordInput

/**
 * Parser di file LRC (testo karaoke temporizzato).
 *
 * Supporta:
 *  - LRC standard per riga: `[mm:ss.xx]testo` (i tempi vengono distribuiti tra le parole);
 *  - Enhanced LRC per parola: `[mm:ss.xx]<mm:ss.xx>par<mm:ss.xx>ola`;
 *  - tag ID: `[ti:titolo]`, `[ar:artista]`, `[offset:+/-ms]`.
 *
 * Restituisce parole pronte per [LyricsAssembler] (con `startMs` già valorizzato),
 * così l'autore può esportare subito o rifinire i tempi nel tool.
 */
object LrcParser {

    data class Result(
        val title: String?,
        val artist: String?,
        val words: List<WordInput>,
        /** true se l'LRC ha tempi per-parola (enhanced); false se solo per-riga. */
        val wordLevel: Boolean
    )

    private val timeTag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
    private val wordTag = Regex("""<(\d+):(\d+)(?:[.:](\d+))?>""")
    private val idTag = Regex("""^\[([a-zA-Z]+):(.*)]$""")
    private val whitespace = Regex("""\s+""")

    private const val DEFAULT_LAST_LINE_MS = 3_000L

    fun parse(text: String): Result {
        var title: String? = null
        var artist: String? = null
        var offsetMs = 0L

        data class TimedLine(val startMs: Long, val content: String)
        val timedLines = ArrayList<TimedLine>()

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val idMatch = idTag.matchEntire(line)
            if (idMatch != null) {
                when (idMatch.groupValues[1].lowercase()) {
                    "ti" -> title = idMatch.groupValues[2].trim().ifBlank { null }
                    "ar" -> artist = idMatch.groupValues[2].trim().ifBlank { null }
                    "offset" -> offsetMs = idMatch.groupValues[2].replace("+", "").trim().toLongOrNull() ?: 0L
                }
                continue
            }

            // Raccoglie i tag tempo iniziali (una riga può ripetersi con più timestamp).
            val times = ArrayList<Long>()
            var idx = 0
            while (true) {
                val m = timeTag.find(line, idx) ?: break
                if (m.range.first != idx) break
                times.add(toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]))
                idx = m.range.last + 1
            }
            if (times.isEmpty()) continue
            val content = line.substring(idx)
            for (t in times) timedLines.add(TimedLine(t, content))
        }

        timedLines.sortBy { it.startMs }

        val words = ArrayList<WordInput>()
        for ((lineIndex, tLine) in timedLines.withIndex()) {
            val lineStart = (tLine.startMs - offsetMs).coerceAtLeast(0)
            val nextStart = timedLines.getOrNull(lineIndex + 1)
                ?.let { (it.startMs - offsetMs).coerceAtLeast(0) }
            parseContent(tLine.content, lineIndex, lineStart, nextStart, offsetMs, words)
        }

        return Result(title, artist, words, wordLevel = wordTag.containsMatchIn(text))
    }

    private fun parseContent(
        content: String,
        lineIndex: Int,
        lineStart: Long,
        nextStart: Long?,
        offsetMs: Long,
        out: MutableList<WordInput>
    ) {
        val wordTags = wordTag.findAll(content).toList()

        if (wordTags.isNotEmpty()) {
            // Enhanced LRC: tempi per parola. Si preserva la spaziatura originale del
            // segmento (gestisce sia parole separate da spazio sia sillabe attaccate).
            val leading = sanitize(content.substring(0, wordTags.first().range.first))
            if (leading.isNotEmpty()) out.add(WordInput(leading, lineIndex, lineStart))

            for ((i, m) in wordTags.withIndex()) {
                val t = (toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]) - offsetMs)
                    .coerceAtLeast(0)
                val segStart = m.range.last + 1
                val segEnd = if (i + 1 < wordTags.size) wordTags[i + 1].range.first else content.length
                val seg = sanitize(content.substring(segStart, segEnd))
                if (seg.isNotEmpty()) out.add(WordInput(seg, lineIndex, t))
            }
        } else {
            // LRC standard: parole intere; ogni token porta uno spazio finale per la resa.
            val tokens = tokenize(content)
            if (tokens.isEmpty()) return
            val lineEnd = nextStart ?: (lineStart + DEFAULT_LAST_LINE_MS)
            val span = (lineEnd - lineStart).coerceAtLeast(0)
            tokens.forEachIndexed { i, token ->
                val start = if (tokens.size > 1) lineStart + span * i / tokens.size else lineStart
                out.add(WordInput("$token ", lineIndex, start))
            }
        }
    }

    private fun tokenize(s: String): List<String> =
        s.trim().split(whitespace).filter { it.isNotBlank() }

    private fun sanitize(s: String): String = s.replace("\r", "").replace("\n", "")

    private fun toMs(min: String, sec: String, frac: String?): Long {
        val minutes = min.toLong()
        val seconds = sec.toLong()
        val fractionMs = when {
            frac.isNullOrEmpty() -> 0L
            frac.length == 1 -> frac.toLong() * 100
            frac.length == 2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return (minutes * 60 + seconds) * 1000 + fractionMs
    }
}
