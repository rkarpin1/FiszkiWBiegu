package pl.rkarpinski.fiszkiwbiegu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Okrągły przycisk kontrolki mediów. `primary = true` to duży ember-button
 * pośrodku triady (play/pause).
 */
@Composable
fun CtrlButton(
    icon: ImageVector,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val size = if (primary) 78.dp else 52.dp
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (primary) scheme.primary else scheme.surface),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (primary) scheme.onPrimary else scheme.onSurface,
            modifier = Modifier.size(if (primary) 30.dp else 22.dp),
        )
    }
}

/** Triada kontrolek prev / play-pause / next. */
@Composable
fun MediaControls(
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier            = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment   = Alignment.CenterVertically,
    ) {
        CtrlButton(Icons.Default.SkipPrevious, onClick = onPrev)
        CtrlButton(
            icon    = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            primary = true,
            onClick = onPlayPause,
        )
        CtrlButton(Icons.Default.SkipNext, onClick = onNext)
    }
}
