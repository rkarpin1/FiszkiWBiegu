package pl.rkarpinski.fiszkiwbiegu.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = koinViewModel(),
    onLogout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    ProfileContent(
        uiState = uiState,
        onLogout = onLogout,
    )
}

@Composable
private fun ProfileContent(
    uiState: ProfileUiState,
    onLogout: () -> Unit,
) {
    FiszkiThemedScreen(naturalDark = true) {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                "Konto",
                style = MaterialTheme.typography.headlineLarge,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))

            // Profile card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(scheme.surface)
                    .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.extraLarge)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar
                val initial = uiState.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(scheme.primary, scheme.secondary)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initial,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimary,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    uiState.displayName.ifBlank { "…" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onSurface,
                )
                if (uiState.email.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        uiState.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Provider badge
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(scheme.surfaceVariant)
                        .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "G",
                            style = MaterialTheme.typography.labelSmall.copy(color = scheme.onPrimary),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    CapsLabel("Zalogowano przez Google", color = scheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.weight(1f))

            Spacer(Modifier.height(16.dp))

            // Wyloguj button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.large)
                    .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.large)
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Wyloguj",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onBackground,
                )
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Preview
@Composable
fun ProfileScreenPreview() {
    ProfileContent(
        uiState = ProfileUiState(
            displayName = "Robert Karpiński",
            email = "rkarpin1@gmail.com",
            streakDays = 5,
        ),
        onLogout = {},
    )
}

@Preview
@Composable
fun ProfileScreenEmptyPreview() {
    ProfileContent(
        uiState = ProfileUiState(),
        onLogout = {},
    )
}
