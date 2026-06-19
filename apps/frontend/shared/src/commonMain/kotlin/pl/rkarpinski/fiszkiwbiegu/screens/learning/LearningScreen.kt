package pl.rkarpinski.fiszkiwbiegu.screens.learning

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.domain.Rating
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.theme.mono
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.CtrlButton
import pl.rkarpinski.fiszkiwbiegu.ui.components.SrsLevelIndicator
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LearningScreen(
    collection: CollectionDto,
    viewModel: LearningViewModel = koinViewModel(key = collection.id) { parametersOf(collection.id) },
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.startSession() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val state by viewModel.state.collectAsState()

    LearningContent(
        collection = collection,
        state = state,
        onBack = { viewModel.stop(); onBack() },
        onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
        onNext = viewModel::next,
        onPrev = viewModel::previous,
        onSetSpeed = viewModel::setSpeed,
        onDontKnow = { viewModel.rate(Rating.DONT_KNOW) },
        onKnowWell = { viewModel.rate(Rating.KNOW_WELL) },
    )
}

@Composable
fun LearningContent(
    collection: CollectionDto,
    state: LearningState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSetSpeed: (Float) -> Unit = {},
    onDontKnow: () -> Unit = {},
    onKnowWell: () -> Unit = {},
) {
    var elapsedSec by remember { mutableStateOf(0) }
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                delay(1000.milliseconds)
                elapsedSec++
            }
        }
    }

    val speeds = listOf(0.5f to "0.50*", 0.75f to "0.75*", 1.0f to "1.0*", 1.25f to "1.25*")

    val card = state.currentCard ?: state.flashcards.getOrNull(state.currentIndex)

    FiszkiThemedScreen(naturalDark = isSystemInDarkTheme()) {
        val c = LocalFiszkiColors.current
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background)
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
                        .clip(MaterialTheme.shapes.medium)
                        .background(scheme.surface)
                        .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Wróć",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                val mins = (elapsedSec / 60).toString().padStart(2, '0')
                val secs = (elapsedSec % 60).toString().padStart(2, '0')
                Text(
                    text = "$mins:$secs",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono()),
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Collection header + progress counter
            CapsLabel("KOLEKCJA")
            Spacer(Modifier.height(4.dp))
            Text(
                collection.name,
                style = MaterialTheme.typography.headlineLarge,
                color = scheme.onBackground,
            )
//            Spacer(Modifier.height(4.dp))
//            if (state.flashcards.isNotEmpty()) {
//                Text(
//                    text = "${(state.currentIndex + 1)} / ${state.flashcards.size}",
//                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono()),
//                    color = c.mute,
//                )
//            }

            Spacer(Modifier.height(24.dp))

            // Card stage
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(scheme.surface)
                    .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.extraLarge)
                    .padding(28.dp),

                horizontalAlignment = Alignment.CenterHorizontally,

                ) {


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when (state.phase) {
                            LearningPhase.SPEAKING_SOURCE -> "SŁUCHAJ"
                            LearningPhase.SPEAKING_TARGET -> "SŁUCHAJ"
                            LearningPhase.IDLE -> "UWAGA"
                            LearningPhase.ANSWER -> "ODPOWIEDZ"
                            LearningPhase.REPEATING -> "POWTÓRZ"
                        },
                        fontFamily = mono(),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.secondary,
                    )

                    Spacer(Modifier.weight(1f))

                    if (card != null) {
                        SrsLevelIndicator(
                            srsLevel = card.decayLevel(),
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {

                    data class CardStageData(
                        val text: String,
                        val style: TextStyle,
                        val color: Color,
                    )

                    if (card != null) {

                        val stageOnTop = when (state.phase) {
                            LearningPhase.IDLE -> CardStageData(
                                "",
                                MaterialTheme.typography.headlineLarge,
                                scheme.onSurfaceVariant,
                            )

                            LearningPhase.SPEAKING_SOURCE -> CardStageData(
                                card.sourceText,
                                MaterialTheme.typography.headlineLarge,
                                scheme.onSurface,
                            )

                            else ->
                                CardStageData(
                                    card.sourceText,
                                    MaterialTheme.typography.headlineLarge,
                                    scheme.onSurfaceVariant,
                                )
                        }

                        val stageOnBottom = when (state.phase) {

                            LearningPhase.SPEAKING_TARGET -> CardStageData(
                                card.targetText,
                                MaterialTheme.typography.headlineLarge,
                                scheme.onSurface,
                            )

                            LearningPhase.REPEATING -> CardStageData(
                                card.targetText,
                                MaterialTheme.typography.headlineLarge,
                                scheme.onSurfaceVariant,
                            )

                            else ->
                                CardStageData(
                                    "",
                                    MaterialTheme.typography.headlineLarge,
                                    scheme.onSurfaceVariant,
                                )

                        }

                        Text(
                            text = stageOnTop.text,
                            style = stageOnTop.style,
                            color = stageOnTop.color,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(16.dp))

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(scheme.outlineVariant)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = stageOnBottom.text,
                            style = stageOnBottom.style,
                            color = stageOnBottom.color,
                            textAlign = TextAlign.Center,
                        )
                    }

                }

                Spacer(Modifier.height(32.dp))

            }

            Spacer(Modifier.height(24.dp))

            // Speed chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.weight(1f))

                speeds.forEach { (value, label) ->
                    val active = state.playbackSpeed == value
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (active) scheme.primary else scheme.surface)
                            .border(
                                1.dp,
                                if (active) scheme.primary else scheme.outlineVariant,
                                MaterialTheme.shapes.medium
                            )
                            .clickable { onSetSpeed(value) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = mono()),
                            color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                        )
                    }

                }

                Spacer(Modifier.weight(1f))

            }

            Spacer(Modifier.height(24.dp))

            // Wiem / Nie wiem
            val isAnswerPhase = state.phase in listOf(
                LearningPhase.ANSWER,
                LearningPhase.SPEAKING_TARGET, LearningPhase.REPEATING
            );

            var dontKnowPressed by remember { mutableStateOf(false) }
            val dontKnowBg by animateColorAsState(
                targetValue = if (dontKnowPressed) Color.Red.copy(alpha = 0.3f) else scheme.surfaceVariant,
                animationSpec = tween(300),
                finishedListener = { dontKnowPressed = false },
                label = "dontKnowBg",
            )

            var knowWellPressed by remember { mutableStateOf(false) }
            val knowWellBg by animateColorAsState(
                targetValue = if (knowWellPressed) Color(0xFF2E7D32).copy(alpha = 0.3f) else scheme.surfaceVariant,
                animationSpec = tween(300),
                finishedListener = { knowWellPressed = false },
                label = "knowWellBg",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(dontKnowBg)
                        .border(
                            1.dp,
                            if (isAnswerPhase) scheme.outline else scheme.outlineVariant,
                            MaterialTheme.shapes.large,
                        )
                        .then(
                            if (isAnswerPhase) Modifier.clickable {
                                dontKnowPressed = true
                                onDontKnow()
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Nie wiem",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isAnswerPhase) scheme.onSurface else c.mute2,
                    )
                }

                CtrlButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    primary = true,
                    onClick = onPlayPause,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(knowWellBg)
                        .border(
                            1.dp,
                            if (isAnswerPhase) scheme.outline else scheme.outlineVariant,
                            MaterialTheme.shapes.large,
                        )
                        .then(
                            if (isAnswerPhase) Modifier.clickable {
                                knowWellPressed = true
                                onKnowWell()
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Wiem!",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isAnswerPhase) scheme.onSurface else c.mute2,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview
@Composable
private fun LearningScreenPreview() {
    val sampleCollection = CollectionDto(
        id = "1",
        userId = "u1",
        name = "Angielski w podróży",
        description = "Podstawowe zwroty",
        sourceLanguage = "pl",
        targetLanguage = "en",
        createdAt = "2023-01-01",
        flashcardCount = 10
    )

    val sampleFlashcards = listOf(
        FlashcardDto(
            id = "c1",
            collectionId = "1",
            sourceText = "Dzień dobry",
            targetText = "Good morning",
            position = 1,
            createdAt = "2023-01-01"
        ),
        FlashcardDto(
            id = "c2",
            collectionId = "1",
            sourceText = "Dziękuję",
            targetText = "Thank you",
            position = 2,
            createdAt = "2023-01-01"
        )
    )

    LearningContent(
        collection = sampleCollection,
        state = LearningState(
            isPlaying = true,
            flashcards = sampleFlashcards,
            currentIndex = 0,
            phase = LearningPhase.SPEAKING_TARGET
        ),
        onBack = {},
        onPlayPause = {},
        onNext = {},
        onPrev = {}
    )
}
