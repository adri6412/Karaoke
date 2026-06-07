package karaoke.author.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import karaoke.author.AuthorState
import karaoke.shared.lyrics.LyricsTiming
import karaoke.shared.model.HighlightMode
import karaoke.shared.model.LyricLine
import karaoke.shared.model.LyricWord

/** Anteprima del testo sincronizzato, identica per logica a quella dell'app Android. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewLyrics(state: AuthorState, modifier: Modifier = Modifier) {
    val doc = state.buildLyricsDoc()
    val position = state.positionMs

    Box(modifier, contentAlignment = Alignment.Center) {
        if (doc.lines.isEmpty()) {
            Text("(anteprima testo)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Box
        }
        val active = LyricsTiming.activeLineIndex(doc, position).coerceAtLeast(0)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (active - 1 >= 0) DimLine(doc.lines[active - 1].text)
            if (state.highlightMode == HighlightMode.LINE) {
                LineWipe(doc.lines[active], position)
            } else {
                CurrentLine(doc.lines[active], position)
            }
            if (active + 1 < doc.lines.size) DimLine(doc.lines[active + 1].text)
        }
    }
}

/** Modalità riga-per-riga: l'intera riga si riempie dal suo timecode a quello successivo. */
@Composable
private fun LineWipe(line: LyricLine, positionMs: Long) {
    val sung = MaterialTheme.colorScheme.primary
    val unsung = MaterialTheme.colorScheme.onSurface
    val f = LyricsTiming.wordProgress(line.startMs, line.endMs, positionMs)
    val brush = Brush.horizontalGradient(0f to sung, f to sung, f to unsung, 1f to unsung)
    Text(
        line.text,
        style = MaterialTheme.typography.headlineSmall.copy(brush = brush, fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun DimLine(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentLine(line: LyricLine, positionMs: Long) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        line.words.forEach { WordText(it, positionMs) }
    }
}

@Composable
private fun WordText(word: LyricWord, positionMs: Long) {
    val sung = MaterialTheme.colorScheme.primary
    val unsung = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)

    when {
        positionMs >= word.endMs -> Text(word.text, color = sung, style = style)
        positionMs < word.startMs -> Text(word.text, color = unsung, style = style)
        else -> {
            val p = LyricsTiming.wordProgress(word.startMs, word.endMs, positionMs)
            val brush = Brush.horizontalGradient(0f to sung, p to sung, p to unsung, 1f to unsung)
            Text(word.text, style = style.copy(brush = brush))
        }
    }
}
