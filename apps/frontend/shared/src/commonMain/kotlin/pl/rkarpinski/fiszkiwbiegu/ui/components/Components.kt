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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.rkarpinski.fiszkiwbiegu.theme.capsMono
import pl.rkarpinski.fiszkiwbiegu.theme.mono

/**
 * Kapsułka „PL→EN" / „1.0×" — drobna mono-etykieta w UI nauki.
 */
@Composable
fun StudyChip(label: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(MaterialTheme.shapes.small)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = mono(),
                color      = scheme.onSurface,
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
    val scheme = MaterialTheme.colorScheme
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
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (filled) accent else scheme.outlineVariant)
            )
        }
    }
}

/**
 * Mała mono-etykieta nagłówka w CAPS — np. „OSTATNIO // KONTYNUUJ".
 */
@Composable
fun CapsLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = capsMono().copy(color = color))
}

/**
 * Wskaźnik poziomu SRS w postaci listków (0-5).
 */
@Composable
fun SrsLevelIndicator(
    srsLevel: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
) {
    val leafCount = (srsLevel * 5).toInt().coerceIn(0, 5)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(leafCount) {
            Icon(
                imageVector = Icons.Rounded.Eco,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
        }
    }
}
