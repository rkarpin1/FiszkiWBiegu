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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = koinViewModel(),
    onLogout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    FiszkiThemedScreen(naturalDark = true) {
        val c = LocalFiszkiColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.surface)
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                "Konto",
                style = MaterialTheme.typography.headlineLarge,
                color = c.text,
            )
            Spacer(Modifier.height(24.dp))

            // Profile card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surface2)
                    .border(1.dp, c.line, RoundedCornerShape(24.dp))
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
                            Brush.linearGradient(listOf(c.accent, c.accentSoft)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initial,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    uiState.displayName.ifBlank { "…" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.text,
                )
                if (uiState.email.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        uiState.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.mute,
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Provider badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface3)
                        .border(1.dp, c.line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "G",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    CapsLabel("Zalogowano przez Google", color = c.mute)
                }
            }

            Spacer(Modifier.weight(1f))

            // Wyloguj button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, c.line, RoundedCornerShape(18.dp))
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = c.mute,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Wyloguj",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}
