package nl.guido.foodtracker.feature.today

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.ReviewSuggestion
import nl.guido.foodtracker.core.model.WeeklyReview
import nl.guido.foodtracker.core.ui.CardShadow
import nl.guido.foodtracker.core.ui.FoodTheme
import nl.guido.foodtracker.core.ui.IconTile
import nl.guido.foodtracker.core.ui.Tint
import nl.guido.foodtracker.core.ui.mealTint
import nl.guido.foodtracker.core.ui.Routes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    val andMore = stringResource(R.string.today_and_more)
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
                is TodayEvent.Added -> {
                    val names = event.names.joinToString(", ")
                    addedAgain.format(if (event.more > 0) andMore.format(names, event.more) else names)
                }
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
                // "Again" can add several things at once, so its message stays longer to read and undo.
                val duration = if (event is TodayEvent.Added) SnackbarDuration.Long else SnackbarDuration.Short
                val result = snackbar.showSnackbar(message, actionLabel = undo, duration = duration)
                if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
            }
        }
    }

    val background = MaterialTheme.colorScheme.background
    // An earlier day gets a light tint, so it never looks like today.
    val earlierTint = MaterialTheme.colorScheme.primary.copy(alpha = EARLIER_DAY_TINT).compositeOver(background)
    Box(Modifier.fillMaxSize().background(if (state?.isToday == false) earlierTint else background)) {
        state?.let { ui ->
            TodayContent(
                ui = ui,
                snackbar = snackbar,
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
    }
}

/** How strongly an earlier day is tinted with the accent colour. */
private const val EARLIER_DAY_TINT = 0.07f

@Composable
private fun TodayContent(
    ui: TodayUiState,
    snackbar: SnackbarHostState,
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
        // The Undo message sits at the bottom of this box, so always just above the buttons, however tall they are.
        Box(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box {
                    Leaf(Modifier.align(Alignment.TopEnd).offset(x = 44.dp, y = (-34).dp))
                    Header(ui, onDay, onProfile)
                }
                if (ui.yesterdayEmpty) {
                    TextButton(
                        onClick = { onDay(ui.today.minusDays(1)) },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.today_yesterday_empty), style = MaterialTheme.typography.titleSmall)
                    }
                }
                RingSection(summary, ui.proteinGoalG, onTarget, onMacros = { showMacros = true })
                ui.review?.let { ReviewCard(it, onDismiss = { onDismissReview(it) }) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.filledMeals.forEach { meal ->
                        MealCard(
                            meal = meal.meal,
                            title = stringResource(mealName(meal.meal)),
                            items = entriesText(meal.entries, ui.readable),
                            kcal = kcalText(meal.kcal, meal.hasEstimate),
                            onClick = { openMeal = meal.meal },
                        ) {
                            AgainButton { onAgain(meal.meal) }
                        }
                    }
                    MealCard(
                        meal = Meal.DRINKS,
                        title = stringResource(R.string.today_meal_drinks),
                        items = if (summary.drinks.entries.isEmpty()) stringResource(R.string.today_drinks_none)
                        else entriesText(summary.drinks.entries, ui.readable),
                        kcal = kcalText(summary.drinks.kcal, summary.drinks.hasEstimate),
                        onClick = { if (summary.drinks.entries.isEmpty()) onAddTo(Meal.DRINKS) else openMeal = Meal.DRINKS },
                    ) {
                        DrinkButton(TodayIcons.Scan, R.string.today_drink_scan, R.string.today_scan_drink, onScanDrink)
                        DrinkButton(TodayIcons.Add, R.string.today_drink_add, R.string.today_add_drink) { onAddTo(Meal.DRINKS) }
                    }
                    summary.nextEmptyMeal?.let { meal ->
                        AddMealCard(
                            meal = meal,
                            readable = ui.readable,
                            lastTime = summary.lastTime[meal].orEmpty(),
                            onAdd = { onAddTo(meal) },
                            onAgain = { onAgain(meal) },
                        )
                    }
                    if (summary.earlierEmptyMeals.isNotEmpty()) EarlierMeals(summary.earlierEmptyMeals, onAddTo)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp))
        }
        BottomActions(onLogFood, onScan, onWeigh, onEatOut)
    }

    openMeal?.let { meal ->
        MealSheet(
            meal = meal,
            entries = mealEntries(summary, meal),
            readable = ui.readable,
            onDismiss = { openMeal = null },
            onChangeAmount = onChangeAmount,
            onRemove = onRemove,
            onAddMore = { openMeal = null; onAddTo(meal) },
            onMove = { to -> openMeal = null; onMove(meal, to) },
            onCopyToToday = if (ui.isToday) null else ({ openMeal = null; onCopyToToday(meal) }),
        )
    }
    if (showMacros) MacroSheet(summary.eaten, ui.proteinGoalG, onDismiss = { showMacros = false })
}

/**
 * The day with ‹ › arrows to look at (and log on) earlier days, the title, and Settings.
 * Today: the date above "Today". An earlier day: "Yesterday" or "3 days ago" above the full date,
 * so the title says exactly which day it is. A dot on ‹ means a recent day has nothing logged.
 */
@Composable
private fun Header(ui: TodayUiState, onDay: (LocalDate) -> Unit, onProfile: () -> Unit) {
    val locale = Locale.forLanguageTag(stringResource(R.string.today_locale))
    val pattern = stringResource(R.string.today_date_pattern)
    val date = ui.date
    val dateText = remember(date, locale, pattern) {
        date.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val daysAgo = ChronoUnit.DAYS.between(date, ui.today).toInt()
    val (small, title) = when {
        ui.isToday -> dateText to stringResource(R.string.today_title)
        daysAgo == 1 -> stringResource(R.string.today_yesterday) to dateText
        else -> stringResource(R.string.today_days_ago, daysAgo) to dateText
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val emptyLabel = stringResource(R.string.today_previous_day_empty)
                val previous = stringResource(R.string.today_previous_day)
                DayArrow(
                    icon = TodayIcons.Back,
                    label = if (ui.emptyDays.isNotEmpty()) "$previous. $emptyLabel" else previous,
                    dot = ui.emptyDays.isNotEmpty(),
                ) { onDay(date.minusDays(1)) }
                Text(
                    small,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ui.canGoForward) {
                    DayArrow(TodayIcons.Forward, stringResource(R.string.today_next_day)) { nextDay(date, ui.today)?.let(onDay) }
                }
            }
            SettingsButton(ui.displayName, onProfile)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = if (ui.isToday) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            if (!ui.isToday) {
                TextButton(onClick = { onDay(ui.today) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.today_back_to_today))
                }
            }
        }
    }
}

/** One button with a gear, the word "Settings" and the person's letter: opens Profile and settings. */
@Composable
private fun SettingsButton(displayName: String, onClick: () -> Unit) {
    val label = stringResource(R.string.today_profile)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = CardShadow,
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label },
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(TodayIcons.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.today_settings), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    displayName.take(1).uppercase(), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** A ‹ or › button next to the date, big enough to tap; [dot] marks a recent empty day further back. */
@Composable
private fun DayArrow(icon: ImageVector, label: String, dot: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        Box {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            if (dot) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(8.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** "Scan" or "Add" on the Drinks card: icon and word, 48 dp high; [spoken] says it is for a drink. */
@Composable
private fun DrinkButton(icon: ImageVector, @StringRes text: Int, @StringRes spoken: Int, onClick: () -> Unit) {
    val description = stringResource(spoken)
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(text), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun RingSection(summary: TodaySummary, proteinGoalG: Int?, onTarget: () -> Unit, onMacros: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = CardShadow) {
        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KcalMeter(summary)
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
                Text(sumText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val eaten = summary.eaten
            val macrosText = if (proteinGoalG != null) {
                stringResource(
                    R.string.today_macros_goal, formatKcal(eaten.protein), formatKcal(proteinGoalG),
                    formatKcal(eaten.carbs), formatKcal(eaten.fat),
                )
            } else {
                stringResource(R.string.today_macros, formatKcal(eaten.protein), formatKcal(eaten.carbs), formatKcal(eaten.fat))
            }
            TextButton(onClick = onMacros, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 32.dp)) {
                Text(macrosText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The Garden half-circle meter: it fills from left to right as the day's kcal are eaten. */
@Composable
private fun KcalMeter(summary: TodaySummary) {
    val left = summary.kcalLeft
    val (number, label, description) = when {
        left == null -> Triple(summary.eatenKcal, R.string.today_kcal_eaten, R.string.today_ring_description_eaten)
        left >= 0 -> Triple(left, R.string.today_kcal_left, R.string.today_ring_description_left)
        else -> Triple(abs(left), R.string.today_kcal_over, R.string.today_ring_description_over)
    }
    val accent = MaterialTheme.colorScheme.primary
    val track = FoodTheme.colors.meterTrack
    val spoken = stringResource(description, formatKcal(number))
    // With big text the number and label may need more room than the arc: the box then grows downwards.
    Box(Modifier.width(240.dp).heightIn(min = 128.dp).clearAndSetSemantics { contentDescription = spoken }) {
        Canvas(Modifier.align(Alignment.TopCenter).size(240.dp, 128.dp)) {
            val line = 18.dp.toPx()
            val radius = 100.dp.toPx()
            val topLeft = Offset(size.width / 2 - radius, 116.dp.toPx() - radius)
            val arc = Size(radius * 2, radius * 2)
            drawArc(track, 180f, 180f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(line, cap = StrokeCap.Round))
            if (summary.ringFraction > 0f) {
                drawArc(
                    accent, 180f, 180f * summary.ringFraction, useCenter = false, topLeft = topLeft, size = arc,
                    style = Stroke(line, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(top = 44.dp).width(150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FitText(formatKcal(number), MaterialTheme.typography.displayLarge)
            Text(
                stringResource(label), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** The small leaf drawing top-right on Today (Garden). Decoration only, so screen readers skip it. */
@Composable
private fun Leaf(modifier: Modifier = Modifier) {
    val colors = FoodTheme.colors
    val parts = remember(colors) {
        listOf(
            "M92 92C80 72 86 56 104 50C110 66 106 82 92 92Z" to colors.accentOutline,
            "M118 72C120 52 134 42 152 44C150 62 138 72 118 72Z" to colors.accent,
            "M76 112C58 104 52 88 60 74C74 82 80 96 76 112Z" to colors.accentTrack,
        ).map { (d, color) -> PathParser().parsePathString(d).toPath() to color }
    }
    val stem = remember { PathParser().parsePathString("M60 130C70 90 100 70 140 62").toPath() }
    Canvas(modifier.size(170.dp, 134.dp)) {
        // Drawn on the design's 190 × 150 grid, scaled to fit.
        scale(size.width / 190f, size.height / 150f, pivot = Offset.Zero) {
            drawCircle(colors.accentSoft, radius = 70f, center = Offset(140f, 40f))
            drawPath(stem, colors.accent, style = Stroke(3f, cap = StrokeCap.Round))
            parts.forEach { (path, color) -> drawPath(path, color) }
        }
    }
}

/**
 * One line of text in [style] that gets smaller until it fits its width (S20): the ring's number
 * stays whole with big text settings or a big number. Nothing is drawn until it fits.
 */
@Composable
private fun FitText(text: String, style: TextStyle) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var fits by remember(text, style) { mutableStateOf(false) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.drawWithContent { if (fits) drawContent() },
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && scale > MIN_FIT_SCALE) scale *= 0.9f else fits = true
        },
    )
}

/** FitText never goes below this share of its style's size. */
private const val MIN_FIT_SCALE = 0.5f

@Composable
private fun MealCard(
    meal: Meal,
    title: String,
    items: String,
    kcal: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(mealIcon(meal), mealTint(meal))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    items, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(kcal, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
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
        Text(stringResource(R.string.today_again), style = MaterialTheme.typography.labelLarge)
    }
}

/** The dashed "+ Add dinner" invitation, with Again when there is a last time to repeat. */
@Composable
private fun AddMealCard(
    meal: Meal,
    readable: Map<String, String>,
    lastTime: List<LogEntry>,
    onAdd: () -> Unit,
    onAgain: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val invite = FoodTheme.colors.invite
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(invite)
            .drawBehind {
                val width = 2.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clip(shape)
            .clickable(role = Role.Button, onClick = onAdd)
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val colors = FoodTheme.colors
        IconTile(mealIcon(meal), Tint(colors.accentSoft, colors.accentStrong), size = 40.dp, iconSize = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(addMeal(meal)), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (lastTime.isNotEmpty()) {
                Text(
                    stringResource(R.string.today_last_time, entriesText(lastTime, readable)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(stringResource(addMeal(meal)), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ReviewCard(review: WeeklyReview, onDismiss: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.today_review_title), style = MaterialTheme.typography.titleMedium,
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
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.size(6.dp))
            Text(stringResource(suggestionText(review.suggestion)), style = MaterialTheme.typography.titleSmall)
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
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = modifier
                .shadow(6.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                .heightIn(min = 58.dp),
        ) {
            Icon(TodayIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.today_log_food_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black),
            )
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.sizeIn(minWidth = 58.dp, minHeight = 58.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
