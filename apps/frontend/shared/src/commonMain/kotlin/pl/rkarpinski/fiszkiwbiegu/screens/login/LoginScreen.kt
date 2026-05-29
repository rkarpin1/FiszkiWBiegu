package pl.rkarpinski.fiszkiwbiegu.screens.login

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pl.rkarpinski.fiszkiwbiegu.theme.FiszkiThemedScreen
import pl.rkarpinski.fiszkiwbiegu.theme.LocalFiszkiColors
import pl.rkarpinski.fiszkiwbiegu.ui.components.CapsLabel

@Composable
fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onSignInClick: () -> Unit,
) {
    FiszkiThemedScreen(naturalDark = true) {
        val c = LocalFiszkiColors.current
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background)
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(56.dp))

            // Brand mark
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        repeat(3) {
                            Box(Modifier.width(18.dp).height(2.dp).background(scheme.onPrimary))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "FiszkiWBiegu",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onBackground,
                )
            }

            Spacer(Modifier.height(48.dp))

            // Hero copy
            CapsLabel("// ZACZNIJ W 5 SEKUND", color = c.accentSoft)
            Spacer(Modifier.height(12.dp))
            Text(
                "Wejdź\ni ruszaj.",
                style = MaterialTheme.typography.displayMedium,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Słuchaj fiszek podczas biegu. Bez ekranu, bez rozpraszania.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.weight(1f))

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.primary,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = scheme.primary)
                }
            } else {
                AuthButton(label = "Kontynuuj z Google", enabled = true, onClick = onSignInClick)
                Spacer(Modifier.height(10.dp))
                AuthButton(label = "Kontynuuj z Apple", enabled = false, onClick = {})
                Spacer(Modifier.height(10.dp))
                AuthButton(label = "Kontynuuj z Facebook", enabled = false, onClick = {})
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AuthButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalFiszkiColors.current
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) scheme.surface else scheme.surfaceVariant)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) scheme.onBackground else c.mute2,
        )
    }
}
