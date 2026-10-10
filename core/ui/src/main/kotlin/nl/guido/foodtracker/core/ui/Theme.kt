package nl.guido.foodtracker.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours: "Forest green", chosen in the design review (decisions.md, 9 Oct 2026).
 * One accent colour, and no red anywhere: even Material's "error" colour is the accent,
 * so nothing in the app ever reads as a warning.
 *
 * These are the light colours. In screens, use MaterialTheme.colorScheme or [FoodTheme.colors],
 * which switch to the dark set when the phone is in dark mode.
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

/** The Forest-green tokens for one mode (light or dark). Read them with [FoodTheme.colors]. */
@Immutable
data class FoodPalette(
    val accent: Color,
    val onAccent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val accentTrack: Color,
    val accentOutline: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
)

val LightFoodPalette = FoodPalette(
    accent = FoodColors.Accent,
    onAccent = Color.White,
    accentStrong = FoodColors.AccentStrong,
    accentSoft = FoodColors.AccentSoft,
    accentTrack = FoodColors.AccentTrack,
    accentOutline = FoodColors.AccentOutline,
    background = FoodColors.Background,
    surface = FoodColors.Surface,
    text = FoodColors.Text,
    textSecondary = FoodColors.TextSecondary,
)

/** Dark Forest green (finding 8): same roles, light green accent on deep green-grey. */
val DarkFoodPalette = FoodPalette(
    accent = Color(0xFF7CC9A1),
    onAccent = Color(0xFF0B2A1D),
    accentStrong = Color(0xFFCDEBD9),
    accentSoft = Color(0xFF1F3A2C),
    accentTrack = Color(0xFF2A4436),
    accentOutline = Color(0xFF4F7A62),
    background = Color(0xFF101814),
    surface = Color(0xFF18221C),
    text = Color(0xFFE6EDE8),
    textSecondary = Color(0xFFA9BAB0),
)

private fun FoodPalette.scheme(dark: Boolean) =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = accentStrong,
        secondary = accentStrong,
        onSecondary = background,
        secondaryContainer = accentSoft,
        onSecondaryContainer = accentStrong,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = accentSoft,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = accentSoft,
        inverseSurface = text,
        inverseOnSurface = background,
        inversePrimary = accentStrong,
        outline = accentOutline,
        outlineVariant = accentTrack,
        error = accentStrong,
        onError = onAccent,
        errorContainer = accentSoft,
        onErrorContainer = accentStrong,
    )

private val LocalFoodPalette = staticCompositionLocalOf { LightFoodPalette }

/** Follows the phone's dark mode setting unless [darkTheme] says otherwise. */
@Composable
fun FoodTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkFoodPalette else LightFoodPalette
    CompositionLocalProvider(LocalFoodPalette provides palette) {
        MaterialTheme(colorScheme = palette.scheme(darkTheme), content = content)
    }
}

object FoodTheme {
    /** The current Forest-green tokens (light or dark), e.g. FoodTheme.colors.accentTrack for chart tracks. */
    val colors: FoodPalette
        @Composable @ReadOnlyComposable get() = LocalFoodPalette.current
}
