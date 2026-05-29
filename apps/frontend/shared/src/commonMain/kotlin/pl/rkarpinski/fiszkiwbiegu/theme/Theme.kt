package pl.rkarpinski.fiszkiwbiegu.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokeny kolorów bez odpowiednika w MD3 — tylko te, których nie ma w MaterialTheme.colorScheme.
 * Pozostałe tokeny (surface, text, mute, line, accent…) są dostępne przez MaterialTheme.colorScheme.
 */
data class FiszkiColors(
    val mute2: Color,       // trzeciorzędny / disabled (brak dokładnego MD3 odpowiednika)
    val accentSoft: Color,  // peach (dark) / ember2 (light) — ciepły akcent używany jako gradient fill
)

val DarkColors = FiszkiColors(
    mute2      = MuteD2,
    accentSoft = Peach,
)

val LightColors = FiszkiColors(
    mute2      = MuteL2,
    accentSoft = Ember2,
)

/** Globalny override: `null` = każdy ekran używa swojego naturalnego motywu. */
val LocalFiszkiThemeOverride = staticCompositionLocalOf<Boolean?> { null }

/** Aktualnie efektywny zestaw kolorów (override > naturalTheme). */
val LocalFiszkiColors = staticCompositionLocalOf { DarkColors }

/**
 * Każdy ekran opakowuje swoją zawartość:
 *
 *   @Composable
 *   fun MyScreen() {
 *       FiszkiThemedScreen(naturalDark = true) {
 *           val scheme = MaterialTheme.colorScheme
 *           Box(Modifier.background(scheme.background)) { ... }
 *       }
 *   }
 */
@Composable
fun FiszkiThemedScreen(
    naturalDark: Boolean,
    content: @Composable () -> Unit,
) {
    val override = LocalFiszkiThemeOverride.current
    val effectiveDark = override ?: naturalDark
    val colors = if (effectiveDark) DarkColors else LightColors
    val scheme = if (effectiveDark) {
        darkColorScheme(
            primary                = Ember,
            onPrimary              = Color.White,
            background             = Bg,
            onBackground           = Cream,
            surface                = Bg2,
            onSurface              = Cream,
            secondary              = Peach,
            onSecondary            = Ink,
            secondaryContainer     = Bg3,
            onSecondaryContainer   = Cream,
            tertiary               = Color(0xFF7B68EE),
            onTertiary             = Color.White,
            error                  = Color(0xFFCF6679),
            onError                = Color.Black,
            surfaceVariant         = Bg3,
            onSurfaceVariant       = MuteD,
            outline                = LineD,
            outlineVariant         = LineD,
            inverseSurface         = Cream,
            inverseOnSurface       = Ink,
        )
    } else {
        lightColorScheme(
            primary                = Ember,
            onPrimary              = Color.White,
            background             = Cream,
            onBackground           = Ink,
            surface                = Color.White,
            onSurface              = Ink,
            secondary              = Ember2,
            onSecondary            = Ink,
            secondaryContainer     = Cream2,
            onSecondaryContainer   = Ink,
            tertiary               = Color(0xFF7B68EE),
            onTertiary             = Color.White,
            error                  = Color(0xFFB00020),
            onError                = Color.White,
            surfaceVariant         = Cream2,
            onSurfaceVariant       = MuteL,
            outline                = LineL,
            outlineVariant         = LineL,
            inverseSurface         = Bg,
            inverseOnSurface       = Cream,
        )
    }

    CompositionLocalProvider(LocalFiszkiColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography  = fiszkiTypography(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small      = RoundedCornerShape(8.dp),
                medium     = RoundedCornerShape(12.dp),
                large      = RoundedCornerShape(18.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}

/**
 * Root aplikacji — opakuj App() w to.
 *
 *   FiszkiAppTheme(override = userPreference) { App() }
 */
@Composable
fun FiszkiAppTheme(
    override: Boolean? = null,   // null = auto, true = force dark, false = force light
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFiszkiThemeOverride provides override) {
        content()
    }
}
