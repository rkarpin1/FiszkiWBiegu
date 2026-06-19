package pl.rkarpinski.fiszkiwbiegu.screens.flashcards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.tooling.preview.Preview
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.theme.mono
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.Flag

@Stable
interface CardFormActions {
    fun onBack()
    fun onSave(newFlashcard: FlashcardDto)
    fun onDelete()
}

@Composable
fun CardFormScreen(
    collection: CollectionDto,
    flashcard: FlashcardDto? = null,
    viewModel: FlashcardsViewModel = koinViewModel(key = collection.id) { parametersOf(collection.id) },
    onBack: () -> Unit,
) {
    CardFormContent(
        collection = collection,
        flashcard = flashcard,

        actions = object : CardFormActions {
            override fun onBack() = onBack()
            override fun onSave(newFlashcard: FlashcardDto) {
                if (flashcard != null) viewModel.updateCard(newFlashcard)
                else viewModel.createCard(newFlashcard)
                onBack()
            }

            override fun onDelete() {
                viewModel.deleteFlashcard(flashcard!!.id)
                onBack()
            }

        }

    )
}

@Composable
fun CardFormContent(
    collection: CollectionDto,
    flashcard: FlashcardDto? = null,
    actions: CardFormActions,
) {
    var draft by remember(flashcard) {
        mutableStateOf(
            flashcard ?: FlashcardDto(
                id = "",
                collectionId = collection.id,
                sourceText = "",
                targetText = "",
                position = 0,
                createdAt = ""
            )
        )
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isValid = draft.sourceText.isNotBlank() && draft.targetText.isNotBlank()
    val isEdit = flashcard != null

    FiszkiThemedScreen(naturalDark = isSystemInDarkTheme()) {
        val c = LocalFiszkiColors.current
        val scheme = MaterialTheme.colorScheme

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Usuń fiszkę") },
                text = { Text("Czy na pewno chcesz usunąć fiszkę \"${draft.sourceText}\"? Tej operacji nie można cofnąć.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        actions.onDelete()
                    }) {
                        Text("Usuń", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Anuluj", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
            )
        }

        Column(
            Modifier.fillMaxSize()
                .background(scheme.background)
                .imePadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = actions::onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(scheme.surface)
                        .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium),
                ) {
                    Icon(
                        if (isEdit) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                        contentDescription = "Wróć",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (isEdit) "Edytuj fiszkę" else "Nowa fiszka",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                if (isEdit) {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(scheme.surface)
                            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Usuń",
                            tint = scheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(40.dp))
                }
            }

            // Collection plaque
            Box(
                modifier = Modifier
                    .padding(horizontal = 26.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(scheme.surface)
                    .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = mono(),
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                Column {
                    Text(
                        text = if (isEdit) "Zmień co\nchcesz" else "Wpisz czego\nchcesz się nauczyć",
                        style = MaterialTheme.typography.headlineLarge,
                        color = scheme.onBackground,
                    )
                }
                Spacer(Modifier.height(20.dp))
                // PL field
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Flag("pl", 22.dp)
                    CapsLabel("POLSKI")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft.sourceText,
                    onValueChange = { draft = draft.copy(sourceText = it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                // Translate row (disabled)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(scheme.outlineVariant),
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(scheme.surface)
                            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.large)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                tint = c.mute2,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Przetłumacz",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.mute2,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(scheme.outlineVariant),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // EN field
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Flag("en", 22.dp)
                    CapsLabel("ANGIELSKI")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft.targetText,
                    onValueChange = { draft = draft.copy(targetText = it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.weight(1f))

                // Sticky CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Button(
                        onClick = {
                            actions.onSave(
                                draft.copy(
                                    sourceText = draft.sourceText.trim(),
                                    targetText = draft.targetText.trim()
                                )
                            )
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary,
                            disabledContainerColor = scheme.surfaceVariant,
                            disabledContentColor = c.mute2,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isEdit) "Zapisz zmiany" else "Dodaj fiszkę",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

private val previewCollection = CollectionDto(
    id = "col1",
    userId = "",
    name = "Moje Fiszki",
    description = "",
    sourceLanguage = "pl",
    targetLanguage = "en",
    createdAt = "",
)

private object NoOpCardFormActions : CardFormActions {
    override fun onBack() {}
    override fun onSave(newFlashcard: FlashcardDto) {}
    override fun onDelete() {}
}

@Preview
@Composable
private fun CardFormNewPreview() {
    CardFormContent(collection = previewCollection, actions = NoOpCardFormActions)
}

@Preview
@Composable
private fun CardFormEditPreview() {
    CardFormContent(
        collection = previewCollection,
        flashcard = FlashcardDto(
            id = "1",
            collectionId = "col1",
            sourceText = "Pies",
            targetText = "Dog",
            position = 0,
            createdAt = "2023-01-01"
        ),
        actions = NoOpCardFormActions,
    )
}
