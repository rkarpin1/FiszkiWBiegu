package pl.rkarpinski.fiszkiwbiegu.screens.learning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.theme.mono
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.MediaControls

@Composable
fun LearningScreen(
    collection: CollectionDto,
    viewModel: LearningViewModel = koinViewModel(key = collection.id) { parametersOf(collection.id) },
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.startSession() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val state by viewModel.state.collectAsState()
    val card = state.flashcards.getOrNull(state.currentIndex)

    var elapsedSec by remember { mutableStateOf(0) }
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                delay(1000)
                elapsedSec++
            }
        }
    }

    var speed by remember { mutableStateOf(1.0f) }
    val speeds = listOf(0.75f to "0.75×", 1.0f to "1.0×", 1.25f to "1.25×", 1.5f to "1.5×")

    FiszkiThemedScreen(naturalDark = true) {
        val c = LocalFiszkiColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.surface)
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // Top bar: back button + elapsed timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.line, RoundedCornerShape(12.dp))
                        .clickable { viewModel.stop(); onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wróć",
                        tint = c.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                val mins = (elapsedSec / 60).toString().padStart(2, '0')
                val secs = (elapsedSec % 60).toString().padStart(2, '0')
                Text(
                    text = "$mins:$secs",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono()),
                    color = c.mute,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Collection header + progress counter
            CapsLabel("KOLEKCJA")
            Spacer(Modifier.height(4.dp))
            Text(
                collection.name,
                style = MaterialTheme.typography.headlineLarge,
                color = c.text,
            )
            Spacer(Modifier.height(4.dp))
            if (state.flashcards.isNotEmpty()) {
                Text(
                    text = "${
                        (state.currentIndex + 1).toString().padStart(2, '0')
                    } / ${state.flashcards.size}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono()),
                    color = c.mute,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Card stage
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.surface2)
                    .border(1.dp, c.line, RoundedCornerShape(28.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (card != null) {
                    Text(
                        text = card.targetText,
                        style = MaterialTheme.typography.displayLarge,
                        color = c.text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = card.sourceText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.mute,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when (state.phase) {
                            LearningPhase.SPEAKING_POLISH -> "Wymawiam po polsku..."
                            LearningPhase.SPEAKING_ENGLISH -> "Wymawiam po angielsku..."
                            LearningPhase.IDLE -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.accentSoft,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Speed chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                speeds.forEach { (value, label) ->
                    val active = speed == value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) c.accent else c.surface2)
                            .border(
                                1.dp,
                                if (active) c.accent else c.line,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { speed = value }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = mono()),
                            color = if (active) c.onAccent else c.mute,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Media controls (centred)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MediaControls(
                    isPlaying = state.isPlaying,
                    onPrev = viewModel::previous,
                    onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
                    onNext = viewModel::next,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Wiem / Nie wiem — disabled stubs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface3)
                        .border(1.dp, c.line, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Nie wiem", style = MaterialTheme.typography.titleMedium, color = c.mute2)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface3)
                        .border(1.dp, c.line, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Wiem!", style = MaterialTheme.typography.titleMedium, color = c.mute2)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
