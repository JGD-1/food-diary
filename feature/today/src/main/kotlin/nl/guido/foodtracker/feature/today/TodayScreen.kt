package nl.guido.foodtracker.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.ReviewSuggestion
import nl.guido.foodtracker.core.model.WeeklyReview
import nl.guido.foodtracker.core.ui.FoodTheme
import nl.guido.foodtracker.core.ui.Routes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The Today tab: "kcal left", meals with Again, Drinks, the Monday review, and the logging buttons. */
@Composable
internal fun TodayRoute(
    navController: NavController,
    backStackEntry: NavBackStackEntry,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val comingSoon = stringResource(R.string.today_coming_soon)
    val undo = stringResource(R.string.today_undo)
    val addedAgain = stringResource(R.string.today_added_again)
    val removed = stringResource(R.string.today_removed)
    val addedTo = stringResource(R.string.today_added_to)
    val movedTo = stringResource(R.string.today_moved_to)
    val copied = stringResource(R.string.today_copied)
    val mealNames = Meal.entries.associateWith { stringResource(mealName(it)) }

    val scope = rememberCoroutineScope()
    fun open(route: String, fallback: String? = null) {
        // The message shows only while another part of the app is still being built.
        if (!navController.navigateSafely(route, fallback)) scope.launch { snackbar.showSnackbar(comingSoon) }
    }

    CameraResults(backStackEntry, viewModel::onCameraResult)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val message = when (event) {
                is TodayEvent.Added -> addedAgain.format(mealNames.getValue(event.meal).lowercase())
                is TodayEvent.Removed -> removed.format(event.entry.displayName)
                is TodayEvent.Logged -> addedTo.format(event.name, mealNames.getValue(event.meal).lowercase())
                is TodayEvent.Moved -> movedTo.format(mealNames.getValue(event.to).lowercase())
                is TodayEvent.Copied -> copied
                is TodayEvent.Open -> {
                    if (!navController.navigateSafely(event.route)) snackbar.showSnackbar(comingSoon)
                    null
                }
            }
            if (message != null) {
                val result = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        state?.let { ui ->
            TodayContent(
                ui = ui,
                onProfile = { open(Routes.PROFILE) },
                onTarget = { open(Routes.ENERGY_TARGET) },
                onDay = viewModel::showDay,
                onAgain = viewModel::again,
                onAddTo = { meal -> open(TodayRoutes.logFood(meal, date = ui.dateArg)) },
                onChangeAmount = viewModel::changeAmount,
                onRemove = viewModel::remove,
                onMove = viewModel::move,
                onCopyToToday = viewModel::copyToToday,
                onDismissReview = viewModel::dismissReview,
                onLogFood = { open(TodayRoutes.logFood(date = ui.dateArg)) },
                onScan = { viewModel.openingCamera(forDrink = false); open(Routes.CAMERA) },
                onScanDrink = { viewModel.openingCamera(forDrink = true); open(Routes.CAMERA_DRINK, Routes.CAMERA) },
                onWeigh = { viewModel.openingCamera(forDrink = false); open(Routes.CAMERA_SCALE, Routes.CAMERA) },
                onEatOut = { open(Routes.eatOut(ui.dateArg), Routes.EAT_OUT) },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

@Composable
private fun TodayContent(
    ui: TodayUiState,
    onProfile: () -> Unit,
    onTarget: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onAgain: (Meal) -> Unit,
    onAddTo: (Meal) -> Unit,
    onChangeAmount: (LogEntry, Double) -> Unit,
    onRemove: (LogEntry) -> Unit,
    onMove: (from: Meal, to: Meal) -> Unit,
    onCopyToToday: (Meal) -> Unit,
    onDismissReview: (WeeklyReview) -> Unit,
    onLogFood: () -> Unit,
    onScan: () -> Unit,
    onScanDrink: () -> Unit,
    onWeigh: () -> Unit,
    onEatOut: () -> Unit,
) {
    val summary = ui.summary
    var openMeal by rememberSaveable { mutableStateOf<Meal?>(null) }
    var showMacros by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(ui, onDay, onProfile)
            RingSection(summary, onTarget, onMacros = { showMacros = true })
            ui.review?.let { ReviewCard(it, onDismiss = { onDismissReview(it) }) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.filledMeals.forEach { meal ->
                    MealCard(
                        title = stringResource(mealName(meal.meal)),
                        items = entriesText(meal.entries),
                        kcal = kcalText(meal.kcal, meal.hasEstimate),
                        onClick = { openMeal = meal.meal },
                    ) {
                        AgainButton { onAgain(meal.meal) }
                    }
                }
                MealCard(
                    title = stringResource(R.string.today_meal_drinks),
                    items = if (summary.drinks.entries.isEmpty()) stringResource(R.string.today_drinks_none)
                    else entriesText(summary.drinks.entries),
                    kcal = kcalText(summary.drinks.kcal, summary.drinks.hasEstimate),
                    onClick = { if (summary.drinks.entries.isEmpty()) onAddTo(Meal.DRINKS) else openMeal = Meal.DRINKS },
                ) {
                    val scanLabel = stringResource(R.string.today_scan_drink)
                    FilledTonalIconButton(
                        onClick = onScanDrink,
                        modifier = Modifier.size(40.dp).semantics { contentDescription = scanLabel },
                    ) {
                        Icon(TodayIcons.Scan, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    val label = stringResource(R.string.today_add_drink)
                    FilledTonalIconButton(
                        onClick = { onAddTo(Meal.DRINKS) },
                        modifier = Modifier.size(40.dp).semantics { contentDescription = label },
                    ) {
                        Icon(TodayIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                summary.nextEmptyMeal?.let { meal ->
                    AddMealCard(
                        meal = meal,
                        lastTime = summary.lastTime[meal].orEmpty(),
                        onAdd = { onAddTo(meal) },
                        onAgain = { onAgain(meal) },
                    )
                }
                if (summary.earlierEmptyMeals.isNotEmpty()) EarlierMeals(summary.earlierEmptyMeals, onAddTo)
            }
        }
        BottomActions(onLogFood, onScan, onWeigh, onEatOut)
    }

    openMeal?.let { meal ->
        MealSheet(
            meal = meal,
            entries = mealEntries(summary, meal),
            onDismiss = { openMeal = null },
            onChangeAmount = onChangeAmount,
            onRemove = onRemove,
            onAddMore = { openMeal = null; onAddTo(meal) },
            onMove = { to -> openMeal = null; onMove(meal, to) },
            onCopyToToday = if (ui.isToday) null else ({ openMeal = null; onCopyToToday(meal) }),
        )
    }
    if (showMacros) MacroSheet(summary.eaten, onDismiss = { showMacros = false })
}

/** The day with ‹ › arrows to look at (and log on) earlier days, the title, and the profile button. */
@Composable
private fun Header(ui: TodayUiState, onDay: (LocalDate) -> Unit, onProfile: () -> Unit) {
    val locale = Locale.forLanguageTag(stringResource(R.string.today_locale))
    val pattern = stringResource(R.string.today_date_pattern)
    val date = ui.date
    val dateText = remember(date, locale, pattern) { date.format(DateTimeFormatter.ofPattern(pattern, locale)) }
    val title = when (date) {
        ui.today -> stringResource(R.string.today_title)
        ui.today.minusDays(1) -> stringResource(R.string.today_yesterday)
        else -> remember(date, locale) { date.format(DateTimeFormatter.ofPattern("EEEE", locale)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayArrow("‹", stringResource(R.string.today_previous_day)) { onDay(date.minusDays(1)) }
                Text(
                    dateText.replaceFirstChar { it.titlecase(locale) },
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ui.canGoForward) {
                    DayArrow("›", stringResource(R.string.today_next_day)) { nextDay(date, ui.today)?.let(onDay) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.replaceFirstChar { it.titlecase(locale) },
                    fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                if (!ui.isToday) {
                    TextButton(onClick = { onDay(ui.today) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.today_back_to_today), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val label = stringResource(R.string.today_profile)
        Surface(
            onClick = onProfile,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(44.dp).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(ui.displayName.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

/** A ‹ or › button next to the date, big enough to tap. */
@Composable
private fun DayArrow(symbol: String, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = label },
    ) {
        Text(symbol, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RingSection(summary: TodaySummary, onTarget: () -> Unit, onMacros: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KcalRing(summary)
        Spacer(Modifier.size(4.dp))
        val target = summary.targetKcal
        val sumText = when {
            target == null -> stringResource(R.string.today_set_target)
            summary.hasEstimate -> stringResource(R.string.today_sum_estimate, formatKcal(target), formatKcal(summary.eatenKcal))
            else -> stringResource(R.string.today_sum, formatKcal(target), formatKcal(summary.eatenKcal))
        }
        val openLabel = stringResource(R.string.today_sum_open)
        TextButton(
            onClick = onTarget,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.heightIn(min = 36.dp).semantics { contentDescription = "$sumText. $openLabel" },
        ) {
            Text(sumText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val eaten = summary.eaten
        val macrosText = stringResource(
            R.string.today_macros, formatKcal(eaten.protein), formatKcal(eaten.carbs), formatKcal(eaten.fat),
        )
        TextButton(onClick = onMacros, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 32.dp)) {
            Text(macrosText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KcalRing(summary: TodaySummary) {
    val left = summary.kcalLeft
    val (number, label, description) = when {
        left == null -> Triple(summary.eatenKcal, R.string.today_kcal_eaten, R.string.today_ring_description_eaten)
        left >= 0 -> Triple(left, R.string.today_kcal_left, R.string.today_ring_description_left)
        else -> Triple(abs(left), R.string.today_kcal_over, R.string.today_ring_description_over)
    }
    val accent = MaterialTheme.colorScheme.primary
    val track = FoodTheme.colors.accentTrack
    val inner = MaterialTheme.colorScheme.surface
    val spoken = stringResource(description, formatKcal(number))
    Box(
        Modifier.size(184.dp).clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(184.dp)) {
            val ring = 20.dp.toPx()
            drawCircle(inner)
            inset(ring / 2) {
                drawArc(track, 0f, 360f, useCenter = false, style = Stroke(ring))
                if (summary.ringFraction > 0f) {
                    drawArc(
                        accent, -90f, 360f * summary.ringFraction, useCenter = false,
                        style = Stroke(ring, cap = StrokeCap.Butt),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
            Text(
                formatKcal(number), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = 46.sp, maxLines = 1, softWrap = false,
            )
            Text(
                stringResource(label), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MealCard(
    title: String,
    items: String,
    kcal: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    items, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(kcal, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            trailing()
        }
    }
}

@Composable
private fun AgainButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        Text(stringResource(R.string.today_again), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** The dashed "+ Add dinner" invitation, with Again when there is a last time to repeat. */
@Composable
private fun AddMealCard(
    meal: Meal,
    lastTime: List<LogEntry>,
    onAdd: () -> Unit,
    onAgain: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawBehind {
                val width = 2.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = androidx.compose.ui.geometry.Offset(width / 2, width / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clip(shape)
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(addMeal(meal)), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (lastTime.isNotEmpty()) {
                Text(
                    stringResource(R.string.today_last_time, entriesText(lastTime)),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (lastTime.isNotEmpty()) AgainButton(onAgain)
    }
}

/** Small "+ Add breakfast" lines for meals earlier today that are still empty. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EarlierMeals(meals: List<Meal>, onAddTo: (Meal) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        meals.forEach { meal ->
            TextButton(
                onClick = { onAddTo(meal) },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(addMeal(meal)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ReviewCard(review: WeeklyReview, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.today_review_title), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(4.dp))
            val lines = buildList {
                review.averageKcal?.let { avg ->
                    add(stringResource(R.string.today_review_average_target, formatKcal(avg), formatKcal(review.targetKcal)))
                }
                add(stringResource(R.string.today_review_days, review.daysLogged))
                val kg = review.weighInKg
                val change = review.changeKg
                add(
                    when {
                        kg == null -> stringResource(R.string.today_review_no_weight)
                        change != null -> stringResource(R.string.today_review_weight_change, formatAmount(kg), formatChange(change))
                        else -> stringResource(R.string.today_review_weight, formatAmount(kg))
                    },
                )
            }
            lines.forEach { Text(it, fontSize = 14.sp) }
            Spacer(Modifier.size(6.dp))
            Text(stringResource(suggestionText(review.suggestion)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.today_review_dismiss)) }
            }
        }
    }
}

private fun suggestionText(s: ReviewSuggestion): Int = when (s) {
    ReviewSuggestion.GOAL_REACHED -> R.string.today_review_goal_reached
    ReviewSuggestion.LOG_MORE_DAYS -> R.string.today_review_log_more
    ReviewSuggestion.WEIGH_IN -> R.string.today_review_weigh_in
    ReviewSuggestion.PLAN_AHEAD -> R.string.today_review_above
    ReviewSuggestion.EAT_ENOUGH -> R.string.today_review_below
    ReviewSuggestion.KEEP_GOING -> R.string.today_review_keep_going
}

/**
 * Log food plus three shortcuts in one row. With large text the shortcuts move to their own
 * row below, so no label gets cut off.
 */
@Composable
private fun BottomActions(onLogFood: () -> Unit, onScan: () -> Unit, onWeigh: () -> Unit, onEatOut: () -> Unit) {
    val stacked = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
    val logFood = @Composable { modifier: Modifier ->
        Button(
            onClick = onLogFood,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = modifier.heightIn(min = 56.dp),
        ) {
            Icon(TodayIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.today_log_food_title), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
    val shortcuts = @Composable { modifier: Modifier ->
        Shortcut(TodayIcons.Scan, stringResource(R.string.today_scan), onScan, modifier)
        Shortcut(TodayIcons.Weigh, stringResource(R.string.today_weigh), onWeigh, modifier)
        Shortcut(TodayIcons.EatOut, stringResource(R.string.today_eat_out), onEatOut, modifier)
    }
    if (stacked) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            logFood(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts(Modifier.weight(1f))
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            logFood(Modifier.weight(1f))
            shortcuts(Modifier)
        }
    }
}

/** From this text size (Android's "large" and up) the bottom buttons take two rows. */
private const val LARGE_TEXT_SCALE = 1.3f

@Composable
private fun Shortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
