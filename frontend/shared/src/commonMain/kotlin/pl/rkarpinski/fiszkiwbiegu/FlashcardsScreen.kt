package pl.rkarpinski.fiszkiwbiegu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    collection: CollectionDto,
    viewModel: FlashcardsViewModel = koinViewModel { parametersOf(collection.id) },
    onBack: () -> Unit,
    onStartLearning: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showFormDialog) {
        FlashcardFormDialog(
            initial = uiState.editingFlashcard,
            onConfirm = { pl, en -> viewModel.saveFlashcard(pl, en) },
            onDismiss = { viewModel.hideFormDialog() },
        )
    }

    uiState.pendingDeleteId?.let { id ->
        val flashcard = uiState.flashcards.find { it.id == id }
        DeleteFlashcardDialog(
            polishText = flashcard?.polishText.orEmpty(),
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    TextButton(
                        onClick = onStartLearning,
                        enabled = uiState.flashcards.isNotEmpty(),
                    ) { Text("▶ Nauka") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.flashcards.isEmpty() -> Text(
                    text = "Brak fiszek. Dodaj pierwszą!",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.flashcards, key = { it.id }) { flashcard ->
                        FlashcardItem(
                            flashcard = flashcard,
                            onEditClick = { viewModel.showEditDialog(flashcard) },
                            onDeleteClick = { viewModel.requestDelete(flashcard.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
            uiState.error?.let {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                ) { Text(it) }
            }
        }
    }
}

@Composable
private fun FlashcardItem(
    flashcard: FlashcardDto,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = flashcard.polishText, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = flashcard.englishText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEditClick) { Text("Edytuj") }
        TextButton(onClick = onDeleteClick) {
            Text("Usuń", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FlashcardFormDialog(
    initial: FlashcardDto?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var polishText by remember { mutableStateOf(initial?.polishText ?: "") }
    var englishText by remember { mutableStateOf(initial?.englishText ?: "") }
    val isValid = polishText.isNotBlank() && englishText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nowa fiszka" else "Edytuj fiszkę") },
        text = {
            Column {
                OutlinedTextField(
                    value = polishText,
                    onValueChange = { polishText = it },
                    label = { Text("Polski") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = englishText,
                    onValueChange = { englishText = it },
                    label = { Text("Angielski") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(polishText.trim(), englishText.trim()) },
                enabled = isValid,
            ) { Text(if (initial == null) "Dodaj" else "Zapisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}

@Composable
private fun DeleteFlashcardDialog(
    polishText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Usuń fiszkę") },
        text = { Text("Czy na pewno chcesz usunąć fiszkę „$polishText"? Tej operacji nie można cofnąć.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Usuń", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}