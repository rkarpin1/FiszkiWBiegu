package pl.rkarpinski.fiszkiwbiegu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(
    collection: CollectionDto,
    viewModel: LearningViewModel = koinViewModel { parametersOf(collection.id) },
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.startSession() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val state by viewModel.state.collectAsState()
    val card = state.flashcards.getOrNull(state.currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection.name) },
                navigationIcon = {
                    TextButton(onClick = { viewModel.stop(); onBack() }) { Text("← Wróć") }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Postęp
            Text(
                text = if (state.flashcards.isNotEmpty()) "${state.currentIndex + 1} / ${state.flashcards.size}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Fiszka
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (card == null) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = card.polishText,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = card.englishText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = when (state.phase) {
                                LearningPhase.SPEAKING_POLISH -> "Wymawiam po polsku..."
                                LearningPhase.SPEAKING_ENGLISH -> "Wymawiam po angielsku..."
                                LearningPhase.IDLE -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Sterowanie
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { viewModel.previous() }) {
                    Text("⏮", style = MaterialTheme.typography.headlineMedium)
                }
                Button(
                    onClick = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
                    modifier = Modifier.size(72.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        text = if (state.isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                TextButton(onClick = { viewModel.next() }) {
                    Text("⏭", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}