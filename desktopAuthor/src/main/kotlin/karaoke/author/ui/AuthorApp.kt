package karaoke.author.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import karaoke.author.AuthorState
import karaoke.author.VideoConvertState
import karaoke.shared.model.HighlightMode
import kotlinx.coroutines.delay

@Composable
fun AuthorApp(authorState: AuthorState, videoState: VideoConvertState) {
    var tab by remember { mutableStateOf(0) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Karaoke (testo)") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Video → .krz") })
            }
            when (tab) {
                0 -> LyricsAuthorTab(authorState)
                else -> VideoConvertTab(videoState)
            }
        }
    }
}

@Composable
private fun LyricsAuthorTab(state: AuthorState) {
    LaunchedEffect(Unit) {
        while (true) {
            state.refreshPosition()
            delay(50)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

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
                Button(onClick = {
                    Dialogs.openFile("Scegli audio MIDI o MP3")?.let { state.loadAudio(it) }
                }) { Text("Carica audio (MIDI/MP3)") }

                OutlinedButton(onClick = {
                    Dialogs.openFile("Importa testo .lrc")?.let { state.importLrc(it) }
                }) { Text("Importa LRC") }

                Button(onClick = {
                    Dialogs.saveFile("Esporta .krz", "${state.title.ifBlank { "brano" }}.krz")
                        ?.let { state.export(it) }
                }) { Text("Esporta .krz") }

                Text(
                    state.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Trasporto
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { state.togglePlay() }) { Text(if (state.isPlaying) "Pausa" else "Play") }
                OutlinedButton(onClick = { state.stop() }) { Text("Stop") }
                Text(formatTime(state.positionMs), color = MaterialTheme.colorScheme.onSurface)
                val max = state.durationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = state.positionMs.toFloat().coerceIn(0f, max),
                    valueRange = 0f..max,
                    enabled = state.durationMs > 0,
                    onValueChange = { state.seekTo(it.toLong()) },
                    modifier = Modifier.weight(1f)
                )
                Text(formatTime(state.durationMs), color = MaterialTheme.colorScheme.onSurface)
            }

            // Tap-sync
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { state.tapNext() },
                    modifier = Modifier.height(48.dp)
                ) { Text("TAP ▶ parola successiva") }

                val nextWord = state.words.getOrNull(state.nextTapIndex)?.text ?: "—"
                Text(
                    "Prossima: $nextWord",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text("Riga-per-riga", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(
                    checked = state.highlightMode == HighlightMode.LINE,
                    onCheckedChange = { state.highlightMode = if (it) HighlightMode.LINE else HighlightMode.WORD }
                )
                OutlinedButton(onClick = { state.applyLyrics() }) { Text("Applica testo") }
                OutlinedButton(onClick = { state.resetTiming() }) { Text("Azzera tempi") }
            }

            HorizontalDivider()

            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Editor testo
                Column(Modifier.weight(1f).fillMaxSize()) {
                    Text("Testo — una riga di testo = una riga di karaoke", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.lyricsText,
                        onValueChange = { state.lyricsText = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = { Text("Incolla qui il testo") }
                    )
                }

                // Anteprima + tabella tempi
                Column(Modifier.weight(1.2f).fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        PreviewLyrics(state, Modifier.fillMaxSize().padding(12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Parole e tempi", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        itemsIndexed(state.words) { index, word ->
                            WordRow(
                                index = index,
                                text = word.text,
                                startMs = word.startMs,
                                isNext = index == state.nextTapIndex,
                                onSet = { state.setWordTime(index, state.positionMs) },
                                onClear = { state.setWordTime(index, null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordRow(
    index: Int,
    text: String,
    startMs: Long?,
    isNext: Boolean,
    onSet: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${index + 1}.", modifier = Modifier.width(36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            startMs?.let { formatTime(it) } ?: "—",
            modifier = Modifier.width(72.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onSet) { Text("Imposta") }
        TextButton(onClick = onClear) { Text("✕") }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000).coerceAtLeast(0)
    return "%d:%02d.%03d".format(minutes, seconds, millis)
}
