package karaoke.app.ui.library

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import karaoke.app.Graph
import karaoke.app.data.Song
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private val repository = Graph.repository

    val songs: StateFlow<List<Song>> = repository.songs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var importing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun import(uri: Uri) {
        viewModelScope.launch {
            importing = true
            runCatching { repository.import(uri) }
                .onFailure { errorMessage = it.message ?: "Importazione non riuscita" }
            importing = false
        }
    }

    fun delete(song: Song) {
        viewModelScope.launch { repository.delete(song) }
    }

    fun dismissError() { errorMessage = null }
}
