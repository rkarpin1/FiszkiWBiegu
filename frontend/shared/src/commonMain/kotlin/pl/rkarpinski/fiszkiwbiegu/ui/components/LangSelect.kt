package pl.rkarpinski.fiszkiwbiegu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LangSelect(
    code: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalFiszkiColors.current
    var showSheet by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Flag(code, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            LanguageNames[code] ?: code,
            style = MaterialTheme.typography.bodyLarge,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = c.mute,
            modifier = Modifier.size(16.dp),
        )
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Box(modifier = Modifier.padding(horizontal = 22.dp)) {
                CapsLabel("WYBIERZ JĘZYK")
            }
            Spacer(Modifier.height(12.dp))
            LanguageNames.entries.forEach { (langCode, langName) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(langCode); showSheet = false }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Flag(langCode, 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        langName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (langCode == code) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
