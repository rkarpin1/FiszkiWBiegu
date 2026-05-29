package pl.rkarpinski.fiszkiwbiegu.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flaga kraju — SVG narysowana przez Canvas. Działa niezależnie od emoji-font'u
 * systemowego. Proporcje 3:2 (standard EU).
 *
 * Użycie: Flag("pl", size = 26.dp)
 */
@Composable
fun Flag(code: String, size: Dp = 28.dp) {
    val w = size * 1.5f
    Box(
        modifier = Modifier
            .width(w)
            .height(size)
            .clip(RoundedCornerShape(size * 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(w, size)) {
            val sw = this.size.width
            val sh = this.size.height
            when (code) {
                "pl" -> {
                    drawRect(Color.White, size = Size(sw, sh / 2f))
                    drawRect(Color(0xFFDC143C), topLeft = Offset(0f, sh / 2f), size = Size(sw, sh / 2f))
                }
                "de" -> {
                    val band = sh / 3f
                    drawRect(Color.Black, size = Size(sw, band))
                    drawRect(Color(0xFFDD0000), topLeft = Offset(0f, band), size = Size(sw, band))
                    drawRect(Color(0xFFFFCE00), topLeft = Offset(0f, 2 * band), size = Size(sw, band))
                }
                "es" -> {
                    val side = sh * 0.25f
                    drawRect(Color(0xFFAA151B), size = Size(sw, side))
                    drawRect(Color(0xFFF1BF00), topLeft = Offset(0f, side), size = Size(sw, sh * 0.5f))
                    drawRect(Color(0xFFAA151B), topLeft = Offset(0f, sh * 0.75f), size = Size(sw, side))
                }
                "fr" -> {
                    val band = sw / 3f
                    drawRect(Color(0xFF002395), size = Size(band, sh))
                    drawRect(Color.White, topLeft = Offset(band, 0f), size = Size(band, sh))
                    drawRect(Color(0xFFED2939), topLeft = Offset(2 * band, 0f), size = Size(band, sh))
                }
                "it" -> {
                    val band = sw / 3f
                    drawRect(Color(0xFF008C45), size = Size(band, sh))
                    drawRect(Color(0xFFF4F5F0), topLeft = Offset(band, 0f), size = Size(band, sh))
                    drawRect(Color(0xFFCD212A), topLeft = Offset(2 * band, 0f), size = Size(band, sh))
                }
                "en", "gb" -> drawUnionJack(sw, sh)
                else -> drawRect(Color.Gray, size = Size(sw, sh))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUnionJack(w: Float, h: Float) {
    val blue = Color(0xFF012169)
    val red  = Color(0xFFC8102E)
    drawRect(blue, size = Size(w, h))
    // Saint Andrew (white diagonals)
    val sw = h / 5f
    drawLine(Color.White, Offset(0f, 0f), Offset(w, h), strokeWidth = sw)
    drawLine(Color.White, Offset(w, 0f), Offset(0f, h), strokeWidth = sw)
    // Red diagonals offset (simplified St Patrick)
    val rw = h / 9f
    drawLine(red, Offset(0f, h * 0.4f), Offset(w, h), strokeWidth = rw)
    drawLine(red, Offset(w, h * 0.4f), Offset(0f, h), strokeWidth = rw)
    // White cross
    val cv = h * 0.3f
    val ch = w * 0.4f
    drawRect(Color.White, topLeft = Offset(0f, h / 2f - cv / 2f), size = Size(w, cv))
    drawRect(Color.White, topLeft = Offset(w / 2f - ch / 2f, 0f), size = Size(ch, h))
    // Red cross
    val cv2 = h * 0.15f
    val ch2 = w * 0.2f
    drawRect(red, topLeft = Offset(0f, h / 2f - cv2 / 2f), size = Size(w, cv2))
    drawRect(red, topLeft = Offset(w / 2f - ch2 / 2f, 0f), size = Size(ch2, h))
}

/** Kod języka → wyświetlana nazwa. Kolejność wpisów = kolejność na liście wyboru. */
val LanguageNames = mapOf(
    "pl" to "Polski",
    "en" to "Angielski",
    "de" to "Niemiecki",
    "es" to "Hiszpański",
    "fr" to "Francuski",
    "it" to "Włoski",
)
