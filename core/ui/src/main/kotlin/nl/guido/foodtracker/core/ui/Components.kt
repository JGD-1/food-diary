package nl.guido.foodtracker.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Meal
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The one header style for every screen (S22): an optional back arrow, the title in the theme's
 * title style, and optional actions on the right (labelled buttons, see conventions.md).
 * Tabs without a back arrow pass onBack = null.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack == null) 16.dp else 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            // Garden: the back arrow sits in a round white button.
            FilledIconButton(
                onClick = onBack,
                modifier = Modifier.padding(end = 6.dp).size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.core_back))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/**
 * One food in a list, as on Log food (S21): [title], then [plainLine], then a detail line such as [foodDetail].
 * For S1 pass title = FoodNames.plain(food) ?: food.name and, when the readable name is used,
 * plainLine = food.name (the unchanged NEVO name).
 */
@Composable
fun FoodRow(
    title: String,
    plainLine: String?,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!plainLine.isNullOrBlank()) {
                    Text(plainLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                }
                if (!detail.isNullOrBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            trailing?.invoke()
        }
    }
}

/** The light shadow under Garden cards (Surface(shadowElevation = CardShadow)). */
val CardShadow: Dp = 2.dp

/**
 * A small rounded square in a soft pastel with a line icon on it (Garden): before a meal,
 * a favourite or a recipe. Decoration only: the text next to it says what it is.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Tint,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.36f)).background(tint.fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint.ink, modifier = Modifier.size(iconSize))
    }
}

/** Each meal's own pastel: breakfast peach, lunch sky, dinner clay, snacks mint, drinks lilac. */
@Composable
fun mealTint(meal: Meal): Tint {
    val tints = FoodTheme.colors.tints
    return when (meal) {
        Meal.BREAKFAST -> tints.peach
        Meal.LUNCH -> tints.sky
        Meal.DINNER -> tints.clay
        Meal.SNACKS -> tints.mint
        Meal.DRINKS -> tints.lilac
    }
}

/** "NEVO · 52 kcal per 100 g", the detail line Log food shows under a food. */
@Composable
fun foodDetail(food: Food): String {
    val source = when (food.source) {
        FoodOrigin.NEVO -> stringResource(R.string.core_source_nevo)
        FoodOrigin.OFF -> food.brand ?: stringResource(R.string.core_source_off)
        FoodOrigin.LABEL, FoodOrigin.MANUAL -> stringResource(R.string.core_source_own)
    }
    val kcal = NumberFormat.getIntegerInstance().format(food.per100g.kcal.roundToInt())
    val per100 = stringResource(if (food.isDrink) R.string.core_per_100ml else R.string.core_per_100g, kcal)
    return "$source · $per100"
}

/**
 * The one amount format (S27): "1 apple ≈ 150 g", or just "150 g" without a label
 * (or when the label already is the amount, e.g. "150 g"). Drinks use ml.
 */
fun amountText(label: String?, grams: Double, isDrink: Boolean = false): String {
    val unit = if (isDrink) "ml" else "g"
    val amount = "${formatGrams(grams)} $unit"
    val name = label?.trim()
    return if (name.isNullOrEmpty() || name == amount || name == "${formatGrams(grams)}$unit") amount else "$name ≈ $amount"
}

/** "150", "12.5": whole numbers without decimals, one decimal under 10. */
fun formatGrams(grams: Double): String {
    val rounded = if (grams >= 10) grams.roundToInt().toDouble() else (grams * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
