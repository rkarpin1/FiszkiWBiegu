package pl.rkarpinski.fiszkiwbiegu.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantyczne tokeny kolorów. Każdy ekran używa LocalFiszkiColors.current
 * zamiast surowych Bg/Cream/Ink — dzięki temu jeden i ten sam composable
 * renderuje się poprawnie w obu motywach.
 */
data class FiszkiColors(
    val dark: Boolean,
    val surface: Color,     // tło ekranu
    val surface2: Color,    // karty / inputy / elevated
    val surface3: Color,    // sub-cards / drobne kontenery
    val text: Color,        // główny tekst
    val textInv: Color,     // odwrotny (na akcencie)
    val mute: Color,        // tekst drugorzędny
    val mute2: Color,       // trzeciorzędny / disabled
    val line: Color,        // ramki / divider'y
    val accent: Color,      // ember — niezmienny
    val accentSoft: Color,  // peach (dark) / ember2 (light)
    val onAccent: Color,    // biały na ember
)

val DarkColors = FiszkiColors(
    dark       = true,
    surface    = Bg,
    surface2   = Bg2,
    surface3   = Bg3,
    text       = Cream,
    textInv    = Ink,
    mute       = MuteD,
    mute2      = MuteD2,
    line       = LineD,
    accent     = Ember,
    accentSoft = Peach,
    onAccent   = Color.White,
)

val LightColors = FiszkiColors(
    dark       = false,
    surface    = Cream,
    surface2   = Color.White,
    surface3   = Cream2,
    text       = Ink,
    textInv    = Cream,
    mute       = MuteL,
    mute2      = MuteL2,
    line       = LineL,
    accent     = Ember,
    accentSoft = Ember2,
    onAccent   = Color.White,
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
 *           val c = LocalFiszkiColors.current
 *           Box(Modifier.background(c.surface)) { ... }
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
            primary       = Ember,
            onPrimary     = Color.White,
            background    = Bg,
            onBackground  = Cream,
            surface       = Bg2,
            onSurface     = Cream,
        )
    } else {
        lightColorScheme(
            primary       = Ember,
            onPrimary     = Color.White,
            background    = Cream,
            onBackground  = Ink,
            surface       = Color.White,
            onSurface     = Ink,
        )
    }

    CompositionLocalProvider(LocalFiszkiColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography  = fiszkiTypography(),
            content     = content,
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
