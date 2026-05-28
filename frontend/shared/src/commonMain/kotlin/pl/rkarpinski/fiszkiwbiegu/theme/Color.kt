package pl.rkarpinski.fiszkiwbiegu.theme

import androidx.compose.ui.graphics.Color

// Paleta „Dawn Run"
val Bg     = Color(0xFF0F0B07)
val Bg2    = Color(0xFF1A140E)
val Bg3    = Color(0xFF261C12)

val Cream  = Color(0xFFF5ECDB)
val Cream2 = Color(0xFFEBE0CB)
val Cream3 = Color(0xFFDBCEB5)

val Ink    = Color(0xFF1A140E)

val Ember  = Color(0xFFF04E23)
val Ember2 = Color(0xFFC73C16)
val Peach  = Color(0xFFFFB47A)

// alphas na ciemnym tle
val MuteD  = Color(0x8CF5ECDB)   // 0.55
val MuteD2 = Color(0x52F5ECDB)   // 0.32
val LineD  = Color(0x1AF5ECDB)   // 0.10

// alphas na jasnym tle (do screenów cream — np. formularza)
val MuteL  = Color(0x8C1A140E)   // 0.55
val MuteL2 = Color(0x521A140E)   // 0.32
val LineL  = Color(0x1A1A140E)   // 0.10

// Deterministyczne kolory akcentów kolekcji (hash z id)
private val accentPalette = listOf(
    Ember,
    Peach,
    Color(0xFF7B68EE),  // medium slate blue
    Color(0xFF20B2AA),  // light sea green
    Color(0xFFFF8C00),  // dark orange
    Color(0xFF9ACD32),  // yellow green
)

fun accentColorForId(id: String): Color =
    accentPalette[id.hashCode().let { if (it < 0) -it else it } % accentPalette.size]
