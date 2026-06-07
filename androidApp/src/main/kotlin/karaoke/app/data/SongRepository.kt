package karaoke.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File

class SongRepository(
    context: Context,
    private val dao: SongDao
) {
    private val appContext = context.applicationContext
    private val importer = SongImporter(appContext)

    val songs: Flow<List<Song>> = dao.observeAll()

    suspend fun byId(id: Long): Song? = dao.byId(id)

    suspend fun import(uri: Uri): Song {
        val song = importer.import(uri)
        val id = dao.insert(song)
        return song.copy(id = id)
    }

    suspend fun delete(song: Song) {
        dao.delete(song)
        runCatching { File(song.filePath).delete() }
    }
}
