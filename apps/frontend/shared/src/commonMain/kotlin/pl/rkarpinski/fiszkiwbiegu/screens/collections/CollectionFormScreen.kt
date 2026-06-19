package pl.rkarpinski.fiszkiwbiegu.screens.collections

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.LangSelect

@Stable
interface CollectionFormActions {
    fun onBack()
    fun onSave(newCollection: CollectionDto)
    fun onDelete(id: String) {}
}

@Composable
fun CollectionFormScreen(
    collection: CollectionDto? = null,
    viewModel: CollectionsViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    CollectionFormContent(
        collection = collection,
        isSubmitting = uiState.isSubmitting,
        actions = object : CollectionFormActions {
            override fun onBack() = onBack()
            override fun onSave(newCollection: CollectionDto) {

                if (collection != null)
                    viewModel.updateCollection(newCollection, onSuccess = onBack)
                else viewModel.createCollection(newCollection, onSuccess = onBack)
            }

            override fun onDelete(id: String) = viewModel.deleteCollection(id, onSuccess = onBack)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionFormContent(
    collection: CollectionDto? = null,
    isSubmitting: Boolean = false,
    actions: CollectionFormActions,
) {
    val isEdit = collection != null
    var draft by remember(collection) {
        mutableStateOf(
            collection ?: CollectionDto(
                id = "",
                userId = "",
                name = "",
                description = "",
                sourceLanguage = "pl",
                targetLanguage = "en",
                createdAt = ""
            )
        )
    }
    var showDeleteSheet by remember { mutableStateOf(false) }
    val isValid =
        draft.name.isNotBlank() && draft.sourceLanguage != draft.targetLanguage && !isSubmitting

    FiszkiThemedScreen(naturalDark = isSystemInDarkTheme()) {
        val c = LocalFiszkiColors.current
        val scheme = MaterialTheme.colorScheme

        Column(Modifier.fillMaxSize().background(scheme.background).imePadding()) {
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
                    text = if (isEdit) "Edytuj kolekcję" else "Nowa kolekcja",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (isEdit) {
                    IconButton(
                        onClick = { showDeleteSheet = true },
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

            // Form fields (heading inside scroll so keyboard doesn't cover it)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp),
            ) {
                Column {
                    Text(
                        text = if (isEdit) "Co\nzmieniamy?" else "Co dziś\ndo nauki?",
                        style = MaterialTheme.typography.displayMedium,
                        color = scheme.onBackground,
                    )
                }
                Spacer(Modifier.height(24.dp))
                CapsLabel("NAZWA")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                CapsLabel("OPIS")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { draft = draft.copy(description = it) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Krótka podpowiedź, np. tematyka albo poziom.",
                            color = scheme.onSurfaceVariant
                        )
                    },
                )
                Spacer(Modifier.height(16.dp))
                CapsLabel("JĘZYK OJCZYSTY")
                Spacer(Modifier.height(6.dp))
                LangSelect(
                    code = draft.sourceLanguage,
                    onSelect = { draft = draft.copy(sourceLanguage = it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                CapsLabel("JĘZYK DO NAUKI")
                Spacer(Modifier.height(6.dp))
                LangSelect(
                    code = draft.targetLanguage,
                    onSelect = { draft = draft.copy(targetLanguage = it) },
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
                                    name = draft.name.trim(),
                                    description = draft.description.trim()
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
                            text = if (isEdit) "Zapisz zmiany" else "Dodaj kolekcję",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        if (showDeleteSheet) {
            ModalBottomSheet(onDismissRequest = { showDeleteSheet = false }) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(scheme.errorContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = scheme.onErrorContainer)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Usunąć kolekcję?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "\"${collection?.name}\" zostanie trwale usunięta. Tej akcji nie można cofnąć.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteSheet = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Anuluj")
                        }
                        Button(
                            onClick = {
                                showDeleteSheet = false
                                collection?.let { actions.onDelete(it.id) }
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = scheme.error),
                        ) {
                            Text("Usuń kolekcję", color = scheme.onError)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

private object NoOpCollectionFormActions : CollectionFormActions {
    override fun onBack() {}
    override fun onSave(newCollection: CollectionDto) {}
}

@Preview
@Composable
fun CollectionFormNewPreview() {
    CollectionFormContent(actions = NoOpCollectionFormActions)
}

@Preview
@Composable
fun CollectionFormEditPreview() {
    CollectionFormContent(
        collection = CollectionDto(
            id = "123",
            userId = "",
            name = "Moja kolekcja",
            description = "Opis kolekcji",
            sourceLanguage = "pl",
            targetLanguage = "en",
            createdAt = "",
        ),
        actions = NoOpCollectionFormActions,
    )
}
