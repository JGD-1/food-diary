package nl.guido.foodtracker.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colours from the chosen design (design canvas, top row).
 * One accent colour, and no red anywhere: even Material's "error" colour is the accent,
 * so nothing in the app ever reads as a warning.
 */
object FoodColors {
    val Accent = Color(0xFF5B3FD9)
    val AccentStrong = Color(0xFF3A2699)   // text on soft accent backgrounds
    val AccentSoft = Color(0xFFECE8FF)     // "Again" button, selected tab
    val AccentTrack = Color(0xFFDCD5FF)    // empty part of the ring
    val AccentOutline = Color(0xFFB9AEF0)  // dashed "Add dinner" outline
    val Background = Color(0xFFF3F0FF)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF4A4560)
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
