package nl.guido.foodtracker.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colours: "Forest green", chosen in the design review (decisions.md, 9 Oct 2026).
 * One accent colour, and no red anywhere: even Material's "error" colour is the accent,
 * so nothing in the app ever reads as a warning.
 */
object FoodColors {
    val Accent = Color(0xFF1F6F50)         // buttons, ring, graphs
    val AccentStrong = Color(0xFF164F39)   // text on tinted backgrounds
    val AccentSoft = Color(0xFFE0EDE4)     // tint: "Again" chips, selected tab
    val AccentTrack = Color(0xFFCFE2D5)    // empty part of the ring
    val AccentOutline = Color(0xFF9CC4AA)  // dashed "Add dinner" outline
    val Background = Color(0xFFEEF4EF)
    val Surface = Color(0xFFFFFFFF)        // cards
    val Text = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF45544B)
}

private val colors = lightColorScheme(
    primary = FoodColors.Accent,
    onPrimary = Color.White,
    primaryContainer = FoodColors.AccentSoft,
    onPrimaryContainer = FoodColors.AccentStrong,
    secondary = FoodColors.AccentStrong,
    secondaryContainer = FoodColors.AccentSoft,
    onSecondaryContainer = FoodColors.AccentStrong,
    background = FoodColors.Background,
    onBackground = FoodColors.Text,
    surface = FoodColors.Surface,
    onSurface = FoodColors.Text,
    surfaceVariant = FoodColors.AccentSoft,
    onSurfaceVariant = FoodColors.TextSecondary,
    surfaceContainer = FoodColors.Surface,
    outline = FoodColors.AccentOutline,
    error = FoodColors.AccentStrong,
    onError = Color.White,
    errorContainer = FoodColors.AccentSoft,
    onErrorContainer = FoodColors.AccentStrong,
)

@Composable
fun FoodTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
