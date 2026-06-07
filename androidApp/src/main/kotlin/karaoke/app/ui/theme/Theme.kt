package karaoke.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KaraokeColors = darkColorScheme(
    primary = Color(0xFF8E7CFF),
    onPrimary = Color(0xFF120A2E),
    secondary = Color(0xFF03DAC5),
    background = Color(0xFF0E0B1A),
    onBackground = Color(0xFFEDEAF6),
    surface = Color(0xFF17122B),
    onSurface = Color(0xFFEDEAF6),
    surfaceVariant = Color(0xFF241D3D),
    onSurfaceVariant = Color(0xFFC9C2E0)
)

@Composable
fun KaraokeTheme(content: @Composable () -> Unit) {
    // L'app usa sempre il tema scuro (più adatto a un contesto "da palco").
    MaterialTheme(colorScheme = KaraokeColors, content = content)
}
