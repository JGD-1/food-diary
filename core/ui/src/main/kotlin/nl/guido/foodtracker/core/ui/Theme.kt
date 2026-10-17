package nl.guido.foodtracker.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colours: "Forest green", chosen in the design review (decisions.md, 9 Oct 2026), in the
 * "Garden" look (decisions.md, 17 Oct 2026): warm cream background, white cards, pastel meal tints.
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
    val MeterTrack = Color(0xFFE3EDE2)     // empty part of Today's meter and the "left in the pot" bar
    val Banner = Color(0xFFDCEBDF)         // the green "Point your camera" card
    val Invite = Color(0xFFFBF8F0)         // inside the dashed "Add dinner" card
    val Divider = Color(0xFFEEEAE0)        // lines between rows on a card
    val Background = Color(0xFFF5F1E6)     // warm cream
    val Surface = Color(0xFFFFFFFF)        // cards
    val Text = Color(0xFF1E2A22)
    val TextSecondary = Color(0xFF4A5A50)
}

/** One soft pastel: [fill] behind an icon, [ink] for the icon (and text) on it. */
@Immutable
data class Tint(val fill: Color, val ink: Color)

/** The five Garden pastels, one per meal (see mealTint) and for recipe tiles. */
@Immutable
data class FoodTints(val peach: Tint, val sky: Tint, val lilac: Tint, val mint: Tint, val clay: Tint) {
    /** All five in a fixed order, to give e.g. each recipe its own steady colour. */
    val all: List<Tint> get() = listOf(peach, clay, mint, sky, lilac)
}

private val LightTints = FoodTints(
    peach = Tint(Color(0xFFF8E3C8), Color(0xFF7A4A12)),
    sky = Tint(Color(0xFFD8E8F3), Color(0xFF1D4766)),
    lilac = Tint(Color(0xFFE6DEF3), Color(0xFF46306E)),
    mint = Tint(Color(0xFFDDEBD3), Color(0xFF2D5420)),
    clay = Tint(Color(0xFFF3D9CC), Color(0xFF6E3420)),
)

/** The same pastels, dimmed for dark mode: deep fills, light inks. */
private val DarkTints = FoodTints(
    peach = Tint(Color(0xFF3B2F21), Color(0xFFF3CC9E)),
    sky = Tint(Color(0xFF1F2E3A), Color(0xFFADCFEA)),
    lilac = Tint(Color(0xFF2D2641), Color(0xFFD0C1F0)),
    mint = Tint(Color(0xFF233323), Color(0xFFB8DAA8)),
    clay = Tint(Color(0xFF3B2721), Color(0xFFF0BCA6)),
)

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
    val meterTrack: Color,
    val banner: Color,
    val invite: Color,
    val divider: Color,
    val tints: FoodTints,
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
    meterTrack = FoodColors.MeterTrack,
    banner = FoodColors.Banner,
    invite = FoodColors.Invite,
    divider = FoodColors.Divider,
    tints = LightTints,
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
    meterTrack = Color(0xFF26392E),
    banner = Color(0xFF1C3226),
    invite = Color(0xFF141E18),
    divider = Color(0xFF26302A),
    tints = DarkTints,
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

/** Nunito (SIL Open Font License, res/raw/nunito_license.txt): rounded and friendly, the Garden font. */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Nunito, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/**
 * The one type scale (S20). Screens use these styles (MaterialTheme.typography.X), never their own
 * fontSize, so all text grows together with the phone's text-size setting.
 *  - displayLarge: the big number in Today's ring (shrinks to fit there)
 *  - headlineLarge: tab titles ("Today", "Weight", "Stats"); headlineSmall: big numbers on cards
 *  - titleLarge: screen headers (ScreenHeader); titleMedium: card titles, food and meal names
 *  - bodyLarge: normal text; bodyMedium: second lines; bodySmall: small print
 *  - labelLarge: buttons and chips; labelMedium/labelSmall: small captions, chart labels
 * Garden weights: big numbers and titles Black, names and buttons ExtraBold, captions Bold.
 */
val FoodTypography = Typography(
    displayLarge = style(46, 50, FontWeight.Black),
    displayMedium = style(34, 40, FontWeight.Black),
    displaySmall = style(28, 34, FontWeight.Black),
    headlineLarge = style(34, 38, FontWeight.Black),
    headlineMedium = style(24, 30, FontWeight.Black),
    headlineSmall = style(22, 28, FontWeight.ExtraBold),
    titleLarge = style(24, 30, FontWeight.Black),
    titleMedium = style(16, 22, FontWeight.ExtraBold),
    titleSmall = style(14, 20, FontWeight.Bold),
    bodyLarge = style(16, 22, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.ExtraBold),
    labelMedium = style(12, 16, FontWeight.Bold),
    labelSmall = style(11, 16, FontWeight.Bold),
)

/**
 * Garden corners: rounder everywhere. Cards (medium) 22 dp, big cards and sheets (large, extraLarge) 28 dp.
 * Buttons stay fully round (Material's own pill shape).
 */
val FoodShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalFoodPalette = staticCompositionLocalOf { LightFoodPalette }

/** Follows the phone's dark mode setting unless [darkTheme] says otherwise. */
@Composable
fun FoodTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkFoodPalette else LightFoodPalette
    CompositionLocalProvider(LocalFoodPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.scheme(darkTheme),
            typography = FoodTypography,
            shapes = FoodShapes,
            content = content,
        )
    }
}

object FoodTheme {
    /** The current Forest-green tokens (light or dark), e.g. FoodTheme.colors.accentTrack for chart tracks. */
    val colors: FoodPalette
        @Composable @ReadOnlyComposable get() = LocalFoodPalette.current
}
