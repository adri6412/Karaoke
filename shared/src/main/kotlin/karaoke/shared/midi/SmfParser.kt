package karaoke.shared.midi

/** Errore di parsing di uno Standard MIDI File. */
class MidiFormatException(message: String) : Exception(message)

/** Evento meta testuale (type 0x01 testo, 0x03 nome traccia, 0x05 lyric). */
data class MidiTextEvent(val tick: Long, val type: Int, val text: String)

/** Cambio di tempo (FF 51): microsecondi per nota da un quarto, a partire da [tick]. */
data class TempoChange(val tick: Long, val microsPerQuarter: Int)

/**
 * Risultato del parsing di un MIDI: divisione temporale, mappa dei tempi ed
 * eventi testuali, con conversione tick → millisecondi.
 */
class ParsedMidi(
    val division: Int,
    val tempoChanges: List<TempoChange>,
    val textEvents: List<MidiTextEvent>
) {
    private val isPpq: Boolean = (division and 0x8000) == 0
    private val ticksPerQuarter: Int = if (isPpq && division > 0) division else 480
    private val smpteTicksPerSecond: Int = if (!isPpq) {
        val framesByte = (division shr 8) and 0xFF
        val fps = 256 - framesByte           // complemento a due: 24/25/29/30
        val ticksPerFrame = division and 0xFF
        (fps * ticksPerFrame).coerceAtLeast(1)
    } else 0

    private val sortedTempos: List<TempoChange> = tempoChanges.sortedBy { it.tick }

    /** Converte una posizione in tick in millisecondi tenendo conto dei cambi di tempo. */
    fun tickToMs(tick: Long): Long {
        if (!isPpq) return (tick * 1000.0 / smpteTicksPerSecond).toLong()
        var ms = 0.0
        var prevTick = 0L
        var microsPerQuarter = 500_000.0   // default 120 BPM
        for (tc in sortedTempos) {
            if (tc.tick >= tick) break
            ms += (tc.tick - prevTick) * microsPerQuarter / ticksPerQuarter / 1000.0
            prevTick = tc.tick
            microsPerQuarter = tc.microsPerQuarter.toDouble()
        }
        ms += (tick - prevTick) * microsPerQuarter / ticksPerQuarter / 1000.0
        return ms.toLong()
    }

    /** Durata stimata in ms (ultimo evento testuale noto). */
    val durationMs: Long
        get() = tickToMs(textEvents.maxOfOrNull { it.tick } ?: 0L)
}

/** Parser di Standard MIDI File (puro Kotlin, condiviso tra Android e desktop). */
object SmfParser {

    fun parse(bytes: ByteArray): ParsedMidi {
        val r = ByteReader(bytes)
        if (r.readAscii(4) != "MThd") throw MidiFormatException("Header MThd mancante")
        val headerLen = r.readInt32()
        r.readInt16() // format (0/1/2) — non necessario qui
        val numTracks = r.readInt16()
        val division = r.readInt16()
        repeat((headerLen - 6).coerceAtLeast(0)) { r.readUInt8() }

        val tempoChanges = ArrayList<TempoChange>()
        val textEvents = ArrayList<MidiTextEvent>()

        for (track in 0 until numTracks) {
            if (r.remaining() < 8) break
            val chunkId = r.readAscii(4)
            val chunkLen = r.readInt32()
            val trackEnd = (r.position + chunkLen).coerceAtMost(bytes.size)
            if (chunkId != "MTrk") { r.seek(trackEnd); continue }

            var tick = 0L
            var runningStatus = 0
            while (r.position < trackEnd) {
                tick += r.readVarLen()
                var status = r.readUInt8()
                if (status < 0x80) {
                    r.unread()              // dato di un messaggio in running status
                    status = runningStatus
                } else if (status < 0xF0) {
                    runningStatus = status
                }

                when {
                    status == 0xFF -> {
                        val metaType = r.readUInt8()
                        val len = r.readVarLen().toInt()
                        val data = r.readBytes(len)
                        when (metaType) {
                            0x51 -> if (data.size >= 3) {
                                val tempo = ((data[0].toInt() and 0xFF) shl 16) or
                                    ((data[1].toInt() and 0xFF) shl 8) or
                                    (data[2].toInt() and 0xFF)
                                tempoChanges.add(TempoChange(tick, tempo))
                            }
                            0x01, 0x03, 0x05 ->
                                textEvents.add(MidiTextEvent(tick, metaType, decodeText(data)))
                            0x2F -> r.seek(trackEnd)  // end of track
                        }
                    }
                    status == 0xF0 || status == 0xF7 -> {
                        val len = r.readVarLen().toInt()
                        r.skip(len)
                        runningStatus = 0
                    }
                    else -> when (status and 0xF0) {
                        0xC0, 0xD0 -> r.skip(1)   // program change / channel aftertouch: 1 byte
                        else -> r.skip(2)         // tutti gli altri: 2 byte
                    }
                }
            }
            r.seek(trackEnd)
        }
        return ParsedMidi(division, tempoChanges, textEvents)
    }

    /** Decodifica il testo: prova UTF-8, ripiega su Latin-1 per i .kar legacy. */
    private fun decodeText(data: ByteArray): String {
        val utf8 = data.toString(Charsets.UTF_8)
        return if (utf8.contains('�')) data.toString(Charsets.ISO_8859_1) else utf8
    }
}

/** Lettore sequenziale big-endian su ByteArray. */
private class ByteReader(private val data: ByteArray) {
    var position = 0
        private set

    fun remaining() = data.size - position
    fun seek(p: Int) { position = p.coerceIn(0, data.size) }
    fun skip(n: Int) { position = (position + n).coerceIn(0, data.size) }
    fun unread() { if (position > 0) position-- }

    fun readUInt8(): Int = data[position++].toInt() and 0xFF
    fun readInt16(): Int = (readUInt8() shl 8) or readUInt8()
    fun readInt32(): Int =
        (readUInt8() shl 24) or (readUInt8() shl 16) or (readUInt8() shl 8) or readUInt8()

    fun readAscii(n: Int): String {
        val end = (position + n).coerceAtMost(data.size)
        val s = String(data, position, end - position, Charsets.US_ASCII)
        position = end
        return s
    }

    fun readBytes(n: Int): ByteArray {
        val end = (position + n).coerceAtMost(data.size)
        val out = data.copyOfRange(position, end)
        position = end
        return out
    }

    fun readVarLen(): Long {
        var value = 0L
        while (position < data.size) {
            val b = readUInt8()
            value = (value shl 7) or (b and 0x7F).toLong()
            if (b and 0x80 == 0) break
        }
        return value
    }
}
