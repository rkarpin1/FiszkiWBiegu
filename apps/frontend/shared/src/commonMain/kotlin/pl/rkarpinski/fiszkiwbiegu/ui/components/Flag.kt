package pl.rkarpinski.fiszkiwbiegu.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import pl.rkarpinski.fiszkiwbiegu.shared.generated.resources.*

/**
 * Flaga kraju — korzysta z zasobów XML w commonMain.
 */
@Composable
fun Flag(code: String, size: Dp = 28.dp) {
    val w = size * 1.5f
    val resource = when (code) {
        "pl" -> Res.drawable.flag_pl
        "en", "gb" -> Res.drawable.flag_en
        "de" -> Res.drawable.flag_de
        "es" -> Res.drawable.flag_es
        "fr" -> Res.drawable.flag_fr
        "it" -> Res.drawable.flag_it
        else -> null
    }

    val shape = RoundedCornerShape(size * 0.18f)
    Box(
        modifier = Modifier
            .width(w)
            .height(size)
            .clip(shape)
            .border(0.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (resource != null) {
            Image(
                painter = painterResource(resource),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
        }
    }
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
