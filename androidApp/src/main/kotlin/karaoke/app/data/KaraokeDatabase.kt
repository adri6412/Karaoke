package karaoke.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Song::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class KaraokeDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}
