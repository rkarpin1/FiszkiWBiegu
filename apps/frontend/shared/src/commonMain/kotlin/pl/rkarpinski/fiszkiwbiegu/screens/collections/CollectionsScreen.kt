package pl.rkarpinski.fiszkiwbiegu.screens.collections

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.theme.accentColorForId
import pl.rkarpinski.fiszkiwbiegu.theme.mono
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.TrackBar

@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel = koinViewModel(),
    onCollectionClick: (CollectionDto) -> Unit,
    onAddClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    FiszkiThemedScreen(naturalDark = true) {
        val c = LocalFiszkiColors.current

        uiState.pendingDeleteId?.let { id ->
            val name = uiState.collections.find { it.id == id }?.name.orEmpty()
            DeleteConfirmationDialog(
                name = name,
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.cancelDelete() },
            )
        }

        Scaffold(
            containerColor = c.surface,
            floatingActionButton = {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(c.text)
                        .clickable { onAddClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj", tint = c.surface)
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                        CapsLabel("CZEŚĆ!")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Twoje kolekcje",
                            style = MaterialTheme.typography.headlineLarge,
                            color = c.text,
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (uiState.collections.isEmpty() && !uiState.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 22.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(c.surface2)
                                        .border(1.dp, c.line, RoundedCornerShape(24.dp))
                                        .padding(22.dp),
                                ) {
                                    Column {
                                        CapsLabel("WYBIERZ KOLEKCJĘ")
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Zacznij od wybrania kolekcji.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = c.mute,
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.collections.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier.padding(
                                        horizontal = 22.dp,
                                        vertical = 6.dp
                                    )
                                ) {
                                    CapsLabel("KOLEKCJE")
                                }
                            }
                            itemsIndexed(
                                uiState.collections,
                                key = { _, col -> col.id },
                            ) { index, collection ->
                                LaneRow(
                                    index = index,
                                    collection = collection,
                                    onClick = { onCollectionClick(collection) },
                                )
                                if (index < uiState.collections.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(c.line),
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = c.accent,
                    )
                }

                uiState.error?.let { err ->
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.loadCollections() }) { Text("Ponów") }
                        },
                    ) { Text(err) }
                }
            }
        }
    }
}

@Composable
private fun LaneRow(
    index: Int,
    collection: CollectionDto,
    onClick: () -> Unit,
) {
    val c = LocalFiszkiColors.current
    val accent = accentColorForId(collection.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = mono(),
                color = c.mute,
            ),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                collection.name,
                style = MaterialTheme.typography.headlineSmall,
                color = c.text,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${collection.flashcardCount} fiszek",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = mono(),
                        color = accent
                    ),
                )
                Text("·", style = MaterialTheme.typography.labelMedium, color = c.mute2)
                Text(
                    formatLastStudied(collection.lastStudied),
                    style = MaterialTheme.typography.labelMedium.copy(color = c.mute),
                )
            }
            Spacer(Modifier.height(8.dp))
            TrackBar(
                progress = collection.progress,
                accent = accent,
                segments = 12,
                height = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.mute)
    }
}

private fun formatLastStudied(lastStudied: String?): String {
    lastStudied ?: return "nie ćwiczono"
    return try {
        val instant = Instant.parse(lastStudied)
        val days = (Clock.System.now() - instant).inWholeDays
        when {
            days == 0L -> "dziś"
            days == 1L -> "wczoraj"
            days < 7L -> "$days dni temu"
            days < 30L -> "${days / 7} tyg. temu"
            else -> "${days / 30} mies. temu"
        }
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun DeleteConfirmationDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Usuń kolekcję") },
        text = { Text("Czy na pewno chcesz usunąć kolekcję \"$name\"? Tej operacji nie można cofnąć.") },
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
