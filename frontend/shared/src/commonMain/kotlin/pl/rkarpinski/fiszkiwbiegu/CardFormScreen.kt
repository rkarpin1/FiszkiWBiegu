package pl.rkarpinski.fiszkiwbiegu

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import pl.rkarpinski.fiszkiwbiegu.data.api.FlashcardDto
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.theme.mono
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.Flag

@Composable
fun CardFormScreen(
    collectionId: String,
    collectionName: String,
    flashcard: FlashcardDto? = null,
    viewModel: FlashcardsViewModel = koinViewModel(key = collectionId) { parametersOf(collectionId) },
    onBack: () -> Unit,
) {
    val isEdit = flashcard != null
    var polishText by remember { mutableStateOf(flashcard?.polishText ?: "") }
    var englishText by remember { mutableStateOf(flashcard?.englishText ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isValid = polishText.isNotBlank() && englishText.isNotBlank()

    FiszkiThemedScreen(naturalDark = true) {
        val c = LocalFiszkiColors.current

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Usuń fiszkę") },
                text = { Text("Czy na pewno chcesz usunąć fiszkę \"${flashcard!!.polishText}\"? Tej operacji nie można cofnąć.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        viewModel.deleteFlashcard(flashcard!!.id)
                        onBack()
                    }) {
                        Text("Usuń", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Anuluj") }
                },
            )
        }

        Column(Modifier.fillMaxSize().background(c.surface)) {
            // Top bar
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
                        if (isEdit) Icons.Default.ArrowBack else Icons.Default.Close,
                        contentDescription = "Wróć",
                        tint = c.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (isEdit) "Edytuj fiszkę" else "Nowa fiszka",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                )
                Spacer(Modifier.weight(1f))
                if (isEdit) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surface2)
                            .border(1.dp, c.line, RoundedCornerShape(12.dp))
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Usuń",
                            tint = c.accent,
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface2)
                    .border(1.dp, c.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = collectionName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = mono(),
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = c.mute,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Heading
            Column(modifier = Modifier.padding(horizontal = 26.dp)) {
                Text(
                    text = if (isEdit) "Zmień co\nchcesz." else "Para słów.\nPolski i angielski.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.text,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Form fields
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp),
            ) {
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
                    value = polishText,
                    onValueChange = { polishText = it },
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
                            .background(c.line),
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(c.surface2)
                            .border(1.dp, c.line, RoundedCornerShape(19.dp))
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
                            .background(c.line),
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
                    value = englishText,
                    onValueChange = { englishText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
            }

            // Sticky CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp)
                    .imePadding(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isValid) c.accent else c.surface3)
                        .then(
                            if (isValid) Modifier.clickable {
                                if (isEdit) viewModel.updateCard(flashcard!!.id, polishText.trim(), englishText.trim())
                                else viewModel.createCard(polishText.trim(), englishText.trim())
                                onBack()
                            } else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isValid) c.onAccent else c.mute2,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isEdit) "Zapisz zmiany" else "Dodaj fiszkę",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isValid) c.onAccent else c.mute2,
                        )
                    }
                }
            }
        }
    }
}
