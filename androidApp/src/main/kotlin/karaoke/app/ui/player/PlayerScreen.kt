package karaoke.app.ui.player

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import karaoke.app.data.Song
import karaoke.app.ui.formatTime
import karaoke.app.player.KaraokePlayer
import karaoke.app.player.KaraokePlayerFactory
import karaoke.app.player.PlaybackState
import karaoke.app.player.VideoKaraokePlayer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(song: Song, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var player by remember(song.id) { mutableStateOf<KaraokePlayer?>(null) }

    DisposableEffect(song.id) {
        var created: KaraokePlayer? = null
        val job = scope.launch {
            created = KaraokePlayerFactory.create(context, song, scope)
            player = created
        }
        onDispose {
            job.cancel()
            created?.release()
            player = null
        }
    }

    KeepScreenOn()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val current = player
        if (current == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            // Controlli a scomparsa: durante il video, una volta nascosti, NIENTE
            // qui dentro ricompone -> l'UI thread resta a riposo e il video (overlay
            // hardware via SurfaceView) scorre fluido.
            var controlsVisible by remember(current) { mutableStateOf(true) }
            var interaction by remember(current) { mutableIntStateOf(0) }

            // Auto-hide dopo 3.5s (resettato a ogni interazione). Per l'audio con
            // testi (nessun video) lasciamo i controlli sempre visibili.
            LaunchedEffect(current, controlsVisible, interaction) {
                if (current is VideoKaraokePlayer && controlsVisible) {
                    kotlinx.coroutines.delay(3500)
                    controlsVisible = false
                }
            }

            // Layer di sfondo: il video NON legge lo stato -> ricomposto una sola volta.
            val tapToggle = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible; interaction++ }

            if (current is VideoKaraokePlayer) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().then(tapToggle),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = current.exoPlayer
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setBackgroundColor(AndroidColor.BLACK)
                        }
                    }
                )
            } else {
                // Audio: i testi necessitano della posizione -> stato raccolto qui,
                // isolato dal resto. (Nessun video da far scattare.)
                AudioContent(player = current, song = song)
            }

            // Errore: ricompone SOLO quando cambia il messaggio (non a ogni tick).
            ErrorOverlay(current.state)

            // Controlli: raccolgono lo stato internamente; visibili solo quando serve.
            AnimatedVisibility(
                visible = controlsVisible || current !is VideoKaraokePlayer,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlaybackControls(
                    stateFlow = current.state,
                    title = song.title,
                    onToggle = { current.togglePlayPause(); interaction++ },
                    onSeek = { current.seekTo(it); interaction++ },
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun AudioContent(player: KaraokePlayer, song: Song) {
    val lyrics = player.lyrics
    if (lyrics != null && lyrics.lines.isNotEmpty()) {
        val state by player.state.collectAsState()
        LyricsView(
            lyrics = lyrics,
            positionMs = state.positionMs,
            highlightMode = player.highlightMode,
            modifier = Modifier.fillMaxSize().padding(bottom = 140.dp)
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                song.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorOverlay(stateFlow: StateFlow<PlaybackState>) {
    val error by remember(stateFlow) {
        stateFlow.map { it.error }.distinctUntilChanged()
    }.collectAsState(initial = null)

    error?.let {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    stateFlow: StateFlow<PlaybackState>,
    title: String,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by stateFlow.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(0L)
    val maxValue = duration.coerceAtLeast(1L).toFloat()
    val sliderValue = (if (dragging) dragValue else state.positionMs.toFloat()).coerceIn(0f, maxValue)
    // Brush allocato una sola volta (non a ogni ricomposizione).
    val scrim = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scrim)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Slider(
            value = sliderValue,
            valueRange = 0f..maxValue,
            enabled = duration > 0,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(sliderValue.toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            FilledIconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausa" else "Riproduci"
                )
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
