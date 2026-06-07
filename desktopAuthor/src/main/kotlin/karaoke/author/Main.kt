package karaoke.author

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import karaoke.author.ui.AuthorApp

private val AuthorColors = darkColorScheme(
    primary = Color(0xFF8E7CFF),
    onPrimary = Color(0xFF120A2E),
    background = Color(0xFF0E0B1A),
    onBackground = Color(0xFFEDEAF6),
    surface = Color(0xFF17122B),
    onSurface = Color(0xFFEDEAF6),
    surfaceVariant = Color(0xFF241D3D),
    onSurfaceVariant = Color(0xFFC9C2E0)
)

fun main() = application {
    val authorState = remember { AuthorState() }
    val videoState = remember { VideoConvertState() }
    val windowState = rememberWindowState(width = 1150.dp, height = 820.dp)

    Window(
        onCloseRequest = { authorState.release(); exitApplication() },
        state = windowState,
        title = "Karaoke Author — generatore formato .krz"
    ) {
        MaterialTheme(colorScheme = AuthorColors) {
            AuthorApp(authorState, videoState)
        }
    }
}
