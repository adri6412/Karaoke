package karaoke.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import karaoke.shared.lyrics.LyricsTiming
import karaoke.shared.model.HighlightMode
import karaoke.shared.model.LyricLine
import karaoke.shared.model.LyricWord
import karaoke.shared.model.LyricsDoc

/**
 * Mostra il testo sincronizzato: riga corrente con evidenziazione progressiva
 * parola-per-parola, più righe adiacenti attenuate per il contesto.
 */
@Composable
fun LyricsView(
    lyrics: LyricsDoc,
    positionMs: Long,
    highlightMode: HighlightMode = HighlightMode.WORD,
    modifier: Modifier = Modifier
) {
    val lines = lyrics.lines
    Box(modifier, contentAlignment = Alignment.Center) {
        if (lines.isEmpty()) {
            Text(
                "(nessun testo sincronizzato)",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Box
        }

        val active = LyricsTiming.activeLineIndex(lyrics, positionMs).coerceAtLeast(0)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (active - 1 >= 0) DimLine(lines[active - 1].text)
            if (highlightMode == HighlightMode.LINE) {
                LineWipe(lines[active], positionMs)
            } else {
                CurrentLine(lines[active], positionMs)
            }
            for (i in (active + 1)..(active + 2)) {
                if (i < lines.size) DimLine(lines[i].text)
            }
        }
    }
}

@Composable
private fun LineWipe(line: LyricLine, positionMs: Long) {
    val sung = MaterialTheme.colorScheme.primary
    val unsung = MaterialTheme.colorScheme.onSurface
    val f = LyricsTiming.wordProgress(line.startMs, line.endMs, positionMs)
    val brush = Brush.horizontalGradient(0f to sung, f to sung, f to unsung, 1f to unsung)
    Text(
        line.text,
        style = MaterialTheme.typography.headlineMedium.copy(brush = brush, fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun DimLine(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentLine(line: LyricLine, positionMs: Long) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        line.words.forEach { word -> WordText(word, positionMs) }
    }
}

@Composable
private fun WordText(word: LyricWord, positionMs: Long) {
    val sung = MaterialTheme.colorScheme.primary
    val unsung = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.headlineMedium

    when {
        positionMs >= word.endMs ->
            Text(word.text, color = sung, style = style, fontWeight = FontWeight.Bold)

        positionMs < word.startMs ->
            Text(word.text, color = unsung, style = style, fontWeight = FontWeight.Bold)

        else -> {
            val p = LyricsTiming.wordProgress(word.startMs, word.endMs, positionMs)
            val brush = Brush.horizontalGradient(
                0f to sung,
                p to sung,
                p to unsung,
                1f to unsung
            )
            Text(
                word.text,
                style = style.copy(brush = brush, fontWeight = FontWeight.Bold)
            )
        }
    }
}
