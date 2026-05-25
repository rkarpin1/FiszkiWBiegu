package pl.rkarpinski.fiszkiwbiegu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import pl.rkarpinski.fiszkiwbiegu.data.api.CollectionDto

@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel = koinViewModel(),
    onCollectionClick: (CollectionDto) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showAddDialog) {
        AddCollectionDialog(
            onConfirm = { viewModel.createCollection(it) },
            onDismiss = { viewModel.hideAddDialog() },
        )
    }

    uiState.pendingDeleteId?.let { id ->
        val name = uiState.collections.find { it.id == id }?.name.orEmpty()
        DeleteConfirmationDialog(
            name = name,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.collections.isEmpty() -> Text(
                    text = "Brak kolekcji. Dodaj pierwszą!",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.collections, key = { it.id }) { collection ->
                        CollectionItem(
                            collection = collection,
                            onClick = { onCollectionClick(collection) },
                            onDeleteClick = { viewModel.requestDelete(collection.id) },
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
private fun CollectionItem(
    collection: CollectionDto,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = collection.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDeleteClick) {
            Text("Usuń", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AddCollectionDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowa kolekcja") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Dodaj") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
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
        text = { Text("Czy na pewno chcesz usunąć kolekcję „$name”? Tej operacji nie można cofnąć.") },
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
