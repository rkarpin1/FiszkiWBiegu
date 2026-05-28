package pl.rkarpinski.fiszkiwbiegu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.theme.capsMono
import pl.rkarpinski.fiszkiwbiegu.theme.mono

/**
 * Kapsułka „PL→EN" / „1.0×" — drobna mono-etykieta w UI nauki.
 */
@Composable
fun StudyChip(label: String, modifier: Modifier = Modifier) {
    val c = LocalFiszkiColors.current
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = mono(),
                color      = c.text,
            ),
        )
    }
}

/**
 * Segmentowy pasek toru. Wypełnia po `progress` w kolorze `accent`,
 * resztę w line. Używany w hero kolekcji i w trybie nauki.
 */
@Composable
fun TrackBar(
    progress: Float,
    accent: Color,
    segments: Int = 24,
    height: Dp = 5.dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalFiszkiColors.current
    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(segments) { i ->
            val filled = i.toFloat() / segments < progress
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (filled) accent else c.line)
            )
        }
    }
}

/**
 * Mała mono-etykieta nagłówka w CAPS — np. „OSTATNIO // KONTYNUUJ".
 */
@Composable
fun CapsLabel(text: String, color: Color = LocalFiszkiColors.current.mute) {
    Text(text, style = capsMono().copy(color = color))
}
