package pl.rkarpinski.fiszkiwbiegu.screens.flashcards

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.rkarpinski.fiszkiwbiegu.NetworkChecker
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.theme.bigNumber
import pl.rkarpinski.fiszkiwbiegu.theme.mono
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.Flag
import pl.rkarpinski.fiszkiwbiegu.ui.components.LanguageNames

@Composable
fun FlashcardsScreen(
    collection: CollectionDto,
    viewModel: FlashcardsViewModel = koinViewModel(key = collection.id) { parametersOf(collection.id) },
    networkChecker: NetworkChecker = koinInject(),
    onBack: () -> Unit,
    onStartLearning: () -> Unit,
    onAddCard: () -> Unit = {},
    onEditCard: (FlashcardDto) -> Unit = {},
    onEditCollection: () -> Unit = {},
    onDeleteCollection: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val isOnline by networkChecker.isOnline.collectAsState()

    var showCollectionMenu by remember { mutableStateOf(false) }
    var showDeleteCollectionDialog by remember { mutableStateOf(false) }

    FiszkiThemedScreen(naturalDark = true) {
        val c = LocalFiszkiColors.current

        uiState.pendingDeleteId?.let { id ->
            val flashcard = uiState.flashcards.find { it.id == id }
            DeleteFlashcardDialog(
                polishText = flashcard?.polishText.orEmpty(),
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.cancelDelete() },
            )
        }

        if (showDeleteCollectionDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteCollectionDialog = false },
                title = { Text("Usuń kolekcję") },
                text = { Text("Czy na pewno chcesz usunąć kolekcję \"${collection.name}\"? Tej operacji nie można cofnąć.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteCollectionDialog = false
                        onDeleteCollection()
                    }) {
                        Text("Usuń", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteCollectionDialog = false }) { Text("Anuluj") }
                },
            )
        }

        Scaffold(
            containerColor = c.surface,
            floatingActionButton = {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(c.text)
                        .clickable { onAddCard() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge, color = c.surface)
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Top bar
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface2)
                                    .border(1.dp, c.line, RoundedCornerShape(12.dp))
                                    .clickable(onClick = onBack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Wróć",
                                    tint = c.text,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            CapsLabel("KOLEKCJA")
                            Spacer(Modifier.weight(1f))
                            Box {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = c.mute,
                                    modifier = Modifier.size(40.dp).clickable { showCollectionMenu = true },
                                )
                                DropdownMenu(
                                    expanded = showCollectionMenu,
                                    onDismissRequest = { showCollectionMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edytuj") },
                                        onClick = { showCollectionMenu = false; onEditCollection() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Usuń", color = MaterialTheme.colorScheme.error) },
                                        onClick = { showCollectionMenu = false; showDeleteCollectionDialog = true },
                                    )
                                }
                            }
                        }
                    }

                    // Hero
                    item {
                        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                            Text(
                                collection.name,
                                style = MaterialTheme.typography.displaySmall,
                                color = c.text,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${collection.sourceLanguage.uppercase()} → ${collection.targetLanguage.uppercase()}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = mono()),
                                color = c.mute,
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Stats row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatTile(label = "FISZEK", value = uiState.flashcards.size.toString(), modifier = Modifier.weight(1f))
                            StatTile(label = "POSTĘP", value = "${(collection.progress * 100).toInt()}%", modifier = Modifier.weight(1f))
                            StatTile(label = "CZAS", value = "—", modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // CTA button
                    item {
                        val ctaEnabled = uiState.flashcards.isNotEmpty() && isOnline
                        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (ctaEnabled) c.accent else c.surface3)
                                    .then(if (ctaEnabled) Modifier.clickable(onClick = onStartLearning) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = if (ctaEnabled) c.onAccent else c.mute2,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        "Słuchaj w biegu",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (ctaEnabled) c.onAccent else c.mute2,
                                    )
                                }
                            }
                            if (!isOnline) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Brak połączenia",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.mute,
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    // Language pair
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Flag(collection.sourceLanguage, 26.dp)
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = c.mute,
                                modifier = Modifier.size(16.dp),
                            )
                            Flag(collection.targetLanguage, 26.dp)
                            Spacer(Modifier.width(8.dp))
                            CapsLabel(
                                "${LanguageNames[collection.sourceLanguage]?.uppercase() ?: collection.sourceLanguage.uppercase()} → ${LanguageNames[collection.targetLanguage]?.uppercase() ?: collection.targetLanguage.uppercase()}"
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Flashcards header
                    item {
                        Box(modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
                            CapsLabel("FISZKI · ${uiState.flashcards.size}")
                        }
                    }

                    // Flashcard items
                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = c.accent)
                            }
                        }
                    } else {
                        itemsIndexed(uiState.flashcards, key = { _, f -> f.id }) { index, flashcard ->
                            FlashcardItem(
                                index = index,
                                flashcard = flashcard,
                                onEditClick = { onEditCard(flashcard) },
                                onDeleteClick = { viewModel.requestDelete(flashcard.id) },
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp)
                                    .height(1.dp)
                                    .background(c.line),
                            )
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }

                uiState.error?.let { err ->
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.loadFlashcards() }) { Text("Ponów") }
                        },
                    ) { Text(err) }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalFiszkiColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CapsLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = bigNumber().copy(fontSize = bigNumber().fontSize * 0.45f), color = c.text)
    }
}

@Composable
private fun FlashcardItem(
    index: Int,
    flashcard: FlashcardDto,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val c = LocalFiszkiColors.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = mono(),
                color = c.mute,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(flashcard.polishText, style = MaterialTheme.typography.bodyLarge, color = c.text)
            Text(flashcard.englishText, style = MaterialTheme.typography.bodyMedium, color = c.mute)
        }
        Box {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = c.mute,
                modifier = Modifier.size(24.dp).clickable { showMenu = true },
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Edytuj") },
                    onClick = { showMenu = false; onEditClick() },
                )
                DropdownMenuItem(
                    text = { Text("Usuń", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDeleteClick() },
                )
            }
        }
    }
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
        text = { Text("Czy na pewno chcesz usunąć fiszkę \"$polishText\"? Tej operacji nie można cofnąć.") },
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
