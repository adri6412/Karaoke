package karaoke.app

import android.content.Context
import androidx.room.Room
import karaoke.app.data.KaraokeDatabase
import karaoke.app.data.SongRepository

/** Service locator minimale: evita un framework DI per un'app di queste dimensioni. */
object Graph {
    lateinit var repository: SongRepository
        private set

    fun init(context: Context) {
        val db = Room.databaseBuilder(
            context.applicationContext,
            KaraokeDatabase::class.java,
            "karaoke.db"
        ).build()
        repository = SongRepository(context.applicationContext, db.songDao())
    }
}
