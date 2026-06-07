package karaoke.author.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import karaoke.author.VideoConvertState
import kotlinx.coroutines.launch

@Composable
fun VideoConvertTab(state: VideoConvertState) {
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Converti un video in .krz", style = MaterialTheme.typography.titleLarge)
        Text(
            "Reencoding a massimo 720p (H.264) mantenendo l'audio invariato — leggero per tablet datati.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.title,
                onValueChange = { state.title = it },
                label = { Text("Titolo") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.artist,
                onValueChange = { state.artist = it },
                label = { Text("Artista") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !state.busy,
                onClick = { Dialogs.openFile("Scegli un video")?.let { state.pickSource(it) } }
            ) { Text("Seleziona video") }

            OutlinedButton(
                enabled = !state.busy,
                onClick = { Dialogs.openFile("Seleziona l'eseguibile ffmpeg")?.let { state.setFfmpeg(it) } }
            ) { Text("Individua ffmpeg") }

            Button(
                enabled = !state.busy && state.sourceFile != null && state.ffmpegPath != null,
                onClick = {
                    Dialogs.saveFile("Esporta video .krz", "${state.title.ifBlank { "video" }}.krz")
                        ?.let { dest -> scope.launch { state.convertAndExport(dest) } }
                }
            ) { Text("Converti ed esporta .krz") }
        }

        state.sourceFile?.let {
            Text("Sorgente: ${it.absolutePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.ffmpegPath?.let {
            Text("ffmpeg: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.busy) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }

        Text(state.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
