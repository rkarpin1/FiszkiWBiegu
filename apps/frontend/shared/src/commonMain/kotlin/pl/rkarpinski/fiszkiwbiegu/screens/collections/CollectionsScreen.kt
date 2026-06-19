package pl.rkarpinski.fiszkiwbiegu.screens.collections

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.accentColorForId
import pl.rkarpinski.fiszkiwbiegu.theme.capsMono
import pl.rkarpinski.fiszkiwbiegu.theme.mono
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.Flag
import pl.rkarpinski.fiszkiwbiegu.ui.components.LanguageNames
import pl.rkarpinski.fiszkiwbiegu.ui.components.TrackBar

@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel = koinViewModel(),
    onCollectionClick: (CollectionDto) -> Unit,
    onResumeLearning: (CollectionDto) -> Unit = onCollectionClick,
    onAddClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    CollectionsScreenContent(
        uiState = uiState,
        onCollectionClick = onCollectionClick,
        onResumeLearning = onResumeLearning,
        onAddClick = onAddClick,
        onConfirmDelete = { viewModel.confirmDelete() },
        onCancelDelete = { viewModel.cancelDelete() },
        onRetry = { viewModel.loadCollections() },
    )
}

@Composable
fun CollectionsScreenContent(
    uiState: CollectionsUiState,
    onCollectionClick: (CollectionDto) -> Unit,
    onResumeLearning: (CollectionDto) -> Unit = onCollectionClick,
    onAddClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    FiszkiThemedScreen(naturalDark = isSystemInDarkTheme()) {
        val scheme = MaterialTheme.colorScheme

        uiState.pendingDeleteId?.let { id ->
            val name = uiState.collections.find { it.id == id }?.name.orEmpty()
            DeleteConfirmationDialog(
                name = name,
                onConfirm = onConfirmDelete,
                onDismiss = onCancelDelete,
            )
        }

        Scaffold(
            containerColor = scheme.background,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onAddClick() },
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    containerColor = scheme.inverseSurface.copy(alpha = 0.5f),
                    contentColor = scheme.inverseOnSurface,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj")
                }
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                // .padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                        CapsLabel("CZEŚĆ!")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Twoje kolekcje",
                            style = MaterialTheme.typography.headlineLarge,
                            color = scheme.onBackground,
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {

                        if (uiState.collections.isEmpty() && !uiState.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 22.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(scheme.surface)
                                        .border(
                                            1.dp,
                                            scheme.outlineVariant,
                                            MaterialTheme.shapes.extraLarge
                                        )
                                        .padding(22.dp),
                                ) {
                                    Column {
                                        CapsLabel("DODAJ NOWĄ KOLEKCJĘ", color = scheme.onSurface)
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Zacznij od utworzenia nowej kolekcji",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.collections.isNotEmpty()) {

                            uiState.lastStudiedCollection?.let { last ->
                                item {
                                    LastUsedHero(
                                        last,
                                        onResume = { onResumeLearning(last) },
                                        onOpen = { onCollectionClick(last) },
                                    )
                                }
                            }


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
                                            .background(scheme.outlineVariant),
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = scheme.primary,
                    )
                }

                uiState.error?.let { err ->
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        action = {
                            TextButton(onClick = onRetry) { Text("Ponów") }
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
    val scheme = MaterialTheme.colorScheme
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
                color = scheme.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                collection.name,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onBackground,
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
                Text(
                    "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    formatLastStudied(collection.lastStudied),
                    style = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurfaceVariant),
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
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}


@Composable
private fun LastUsedHero(
    collection: CollectionDto,
    onResume: () -> Unit,
    onOpen: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onOpen)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(scheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(
                "OSTATNIO  //  KONTYNUUJ",
                style = capsMono().copy(color = accentColorForId(collection.id)),
            )
        }
        Spacer(Modifier.height(14.dp))


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Flag(collection.sourceLanguage, 16.dp)
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Flag(collection.targetLanguage, 16.dp)
            Spacer(Modifier.width(8.dp))
            CapsLabel(
                "${LanguageNames[collection.sourceLanguage]?.uppercase() ?: collection.sourceLanguage.uppercase()} → ${LanguageNames[collection.targetLanguage]?.uppercase() ?: collection.targetLanguage.uppercase()}"
            )
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            collection.name,
            style = MaterialTheme.typography.headlineLarge.copy(color = scheme.onSurface),
        )

        Spacer(Modifier.height(14.dp))
        TrackBar(
            progress = collection.progress,
            accent = scheme.primary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))


        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopEnd
        ) {
            Button(
                onClick = onResume,
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 22.dp),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Wznów",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }

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
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Preview
@Composable
fun CollectionsScreenPreview() {
    val sampleCollections = listOf(
        CollectionDto(
            id = "1",
            userId = "u1",
            name = "Angielski - Podstawy",
            description = "Najważniejsze zwroty",
            sourceLanguage = "pl",
            targetLanguage = "en",
            createdAt = "2023-01-01",
            lastStudied = "2023-10-25T10:00:00Z",
            progress = 0.65f,
            flashcardCount = 120
        ),
        CollectionDto(
            id = "2",
            userId = "u1",
            name = "Niemiecki - Podróże",
            description = "Słówka przydatne w podróży",
            sourceLanguage = "pl",
            targetLanguage = "de",
            createdAt = "2023-02-15",
            lastStudied = "2023-11-10T15:30:00Z",
            progress = 0.3f,
            flashcardCount = 50
        ),
        CollectionDto(
            id = "3",
            userId = "u1",
            name = "Hiszpański - Jedzenie",
            description = "W restauracji i sklepie",
            sourceLanguage = "pl",
            targetLanguage = "es",
            createdAt = "2023-03-20",
            lastStudied = null,
            progress = 0.0f,
            flashcardCount = 85
        )
    )

    val uiState = CollectionsUiState(
        collections = sampleCollections,
        isLoading = false
    )

    CollectionsScreenContent(
        uiState = uiState,
        onCollectionClick = {},
        onAddClick = {},
        onConfirmDelete = {},
        onCancelDelete = {},
        onRetry = {}
    )
}

@Preview
@Composable
fun CollectionsScreenPreview2() {
    val sampleCollections = listOf<CollectionDto>()
    val uiState = CollectionsUiState(
        collections = sampleCollections,
        isLoading = false
    )

    CollectionsScreenContent(
        uiState = uiState,
        onCollectionClick = {},
        onAddClick = {},
        onConfirmDelete = {},
        onCancelDelete = {},
        onRetry = {}
    )
}

