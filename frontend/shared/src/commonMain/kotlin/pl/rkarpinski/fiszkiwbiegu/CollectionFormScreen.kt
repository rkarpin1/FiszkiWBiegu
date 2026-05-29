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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel
import pl.rkarpinski.fiszkiwbiegu.ui.components.LangSelect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionFormScreen(
    collectionId: String? = null,
    collectionName: String = "",
    collectionDescription: String = "",
    sourceLanguage: String = "pl",
    targetLanguage: String = "en",
    viewModel: CollectionsViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    val isEdit = collectionId != null
    var name by remember { mutableStateOf(collectionName) }
    var description by remember { mutableStateOf(collectionDescription) }
    var sourceLang by remember { mutableStateOf(sourceLanguage) }
    var targetLang by remember { mutableStateOf(targetLanguage) }
    var showDeleteSheet by remember { mutableStateOf(false) }
    val isValid = name.isNotBlank() && sourceLang != targetLang

    FiszkiThemedScreen(naturalDark = true) {
        val c = LocalFiszkiColors.current

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
                    text = if (isEdit) "Edytuj kolekcję" else "Nowa kolekcja",
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
                            .clickable { showDeleteSheet = true },
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

            // Heading
            Column(modifier = Modifier.padding(horizontal = 26.dp)) {
                Text(
                    text = if (isEdit) "Co\nzmieniamy?" else "Co dziś\ndo worka?",
                    style = MaterialTheme.typography.displayMedium,
                    color = c.text,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Form fields
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp),
            ) {
                CapsLabel("NAZWA")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                CapsLabel("OPIS")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Krótka podpowiedź, np. tematyka albo poziom.", color = c.mute) },
                )
                Spacer(Modifier.height(16.dp))
                CapsLabel("J. OJCZYSTY")
                Spacer(Modifier.height(6.dp))
                LangSelect(
                    code = sourceLang,
                    onSelect = { sourceLang = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                CapsLabel("J. DO NAUKI")
                Spacer(Modifier.height(6.dp))
                LangSelect(
                    code = targetLang,
                    onSelect = { targetLang = it },
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
                                if (isEdit) viewModel.updateCollection(collectionId!!, name.trim(), description.trim(), sourceLang, targetLang)
                                else viewModel.createCollection(name.trim(), description.trim(), sourceLang, targetLang)
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
                            text = if (isEdit) "Zapisz zmiany" else "Dodaj kolekcję",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isValid) c.onAccent else c.mute2,
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = c.accent)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Usunąć kolekcję?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "\"$collectionName\" zostanie trwale usunięta. Tej akcji nie można cofnąć.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.mute,
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
                                collectionId?.let { id ->
                                    viewModel.deleteCollection(id)
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                        ) {
                            Text("Usuń kolekcję", color = c.onAccent)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
