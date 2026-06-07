package karaoke.shared.lyrics

import karaoke.shared.model.LyricsDoc

/** Utility di runtime per allineare il testo alla posizione di riproduzione. */
object LyricsTiming {

    /**
     * Indice della riga attiva alla posizione [positionMs], oppure -1 se la
     * riproduzione è prima dell'inizio della prima riga.
     */
    fun activeLineIndex(doc: LyricsDoc, positionMs: Long): Int {
        var idx = -1
        for (i in doc.lines.indices) {
            if (doc.lines[i].startMs <= positionMs) idx = i else break
        }
        return idx
    }

    /** Frazione di riempimento (0f..1f) di una parola alla posizione corrente. */
    fun wordProgress(startMs: Long, endMs: Long, positionMs: Long): Float {
        if (positionMs <= startMs) return 0f
        if (positionMs >= endMs || endMs <= startMs) return 1f
        return ((positionMs - startMs).toFloat() / (endMs - startMs).toFloat())
            .coerceIn(0f, 1f)
    }
}
