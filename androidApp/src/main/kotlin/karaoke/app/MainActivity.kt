package karaoke.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import karaoke.app.data.Song
import karaoke.app.ui.library.LibraryScreen
import karaoke.app.ui.player.PlayerScreen
import karaoke.app.ui.theme.KaraokeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KaraokeTheme {
                KaraokeRoot()
            }
        }
    }
}

private sealed interface Screen {
    data object Library : Screen
    data class Player(val song: Song) : Screen
}

@Composable
private fun KaraokeRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    when (val s = screen) {
        is Screen.Library -> LibraryScreen(onPlay = { screen = Screen.Player(it) })
        is Screen.Player -> {
            PlayerScreen(song = s.song, onBack = { screen = Screen.Library })
            BackHandler { screen = Screen.Library }
        }
    }
}
