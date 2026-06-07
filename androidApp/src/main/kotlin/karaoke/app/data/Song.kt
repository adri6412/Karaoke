package karaoke.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** Formati supportati dall'app. */
enum class SongFormat { MIDI, KAR, MP4, KRZ }

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val format: SongFormat,
    /** Percorso del file copiato nello storage interno dell'app. */
    val filePath: String,
    val durationMs: Long,
    val addedAt: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter fun fromFormat(value: SongFormat): String = value.name
    @TypeConverter fun toFormat(value: String): SongFormat = SongFormat.valueOf(value)
}
