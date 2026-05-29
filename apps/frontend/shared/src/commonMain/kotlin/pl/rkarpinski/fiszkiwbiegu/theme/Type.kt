package pl.rkarpinski.fiszkiwbiegu.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import pl.rkarpinski.fiszkiwbiegu.shared.generated.resources.Res
import pl.rkarpinski.fiszkiwbiegu.shared.generated.resources.bricolage_grotesque_bold
import pl.rkarpinski.fiszkiwbiegu.shared.generated.resources.bricolage_grotesque_regular
import pl.rkarpinski.fiszkiwbiegu.shared.generated.resources.bricolage_grotesque_semibold
import pl.rkarpinski.fiszkiwbiegu.shared.generated.resources.jetbrains_mono_bold
import pl.rkarpinski.fiszkiwbiegu.shared.generated.resources.jetbrains_mono_regular

@Composable
fun bricolage(): FontFamily = FontFamily(
    Font(Res.font.bricolage_grotesque_regular,  FontWeight.Normal),
    Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(Res.font.bricolage_grotesque_bold,     FontWeight.Bold),
)

@Composable
fun mono(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_bold,    FontWeight.Bold),
)

@Composable
fun fiszkiTypography(): Typography {
    val ui = bricolage()
    return Typography(
        displayLarge   = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold,     fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-1.8).sp),
        displayMedium  = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold,     fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.4).sp),
        displaySmall   = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold,     fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.0).sp),

        headlineLarge  = TextStyle(fontFamily = ui, fontWeight = FontWeight.Bold,     fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.9).sp),
        headlineMedium = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),
        headlineSmall  = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp),

        titleLarge     = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
        titleMedium    = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        titleSmall     = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),

        bodyLarge      = TextStyle(fontFamily = ui, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium     = TextStyle(fontFamily = ui, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall      = TextStyle(fontFamily = ui, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),

        labelLarge     = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
        labelMedium    = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall     = TextStyle(fontFamily = ui, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
    )
}

/** Mała etykieta CAPS + mono + tracking — np. „KOLEKCJA", „OSTATNIO". */
@Composable
fun capsMono(): TextStyle = TextStyle(
    fontFamily    = mono(),
    fontWeight    = FontWeight.Bold,
    fontSize      = 11.sp,
    lineHeight    = 14.sp,
    letterSpacing = 1.0.sp,
)

/** Duże liczniki (indeksy, pace). */
@Composable
fun bigNumber(): TextStyle = TextStyle(
    fontFamily    = mono(),
    fontWeight    = FontWeight.Bold,
    fontSize      = 64.sp,
    lineHeight    = 60.sp,
    letterSpacing = (-2.5).sp,
)
