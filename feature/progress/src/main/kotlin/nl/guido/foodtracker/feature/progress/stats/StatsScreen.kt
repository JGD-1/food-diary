package nl.guido.foodtracker.feature.progress.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.ui.FoodColors
import nl.guido.foodtracker.feature.progress.Format
import nl.guido.foodtracker.feature.progress.NoteText
import nl.guido.foodtracker.feature.progress.R
import nl.guido.foodtracker.feature.progress.SectionCard
import nl.guido.foodtracker.feature.progress.SectionTitle
import nl.guido.foodtracker.feature.progress.TabHeader
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

@Composable
internal fun StatsRoute(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsScreen(state)
}

@Composable
internal fun StatsScreen(state: StatsUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabHeader(
            kicker = stringResource(R.string.progress_stats_kicker),
            title = stringResource(R.string.progress_stats_title),
        )
        if (state is StatsUiState.Ready) {
            ThisWeekCard(state.week, state.targetKcal)
            ByMonthCard(state.months)
            DrinksCard(state.week)
        }
    }
}

@Composable
private fun ThisWeekCard(week: WeekStats, targetKcal: Int?) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            SectionTitle(stringResource(R.string.progress_stats_this_week))
            NoteText(Format.weekRange(week.from, week.to))
        }
        if (week.averageKcal != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text(Format.kcal(week.averageKcal), fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
                NoteText(
                    if (targetKcal != null) {
                        stringResource(R.string.progress_stats_average_target, Format.kcal(targetKcal.toDouble()))
                    } else {
                        stringResource(R.string.progress_stats_average)
                    },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        } else {
            NoteText(stringResource(R.string.progress_stats_week_empty))
        }
        WeekBars(week, targetKcal)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            week.days.forEach { day ->
                val isToday = day.kind == DayKind.TODAY
                Text(
                    if (isToday) {
                        stringResource(R.string.progress_stats_today)
                    } else {
                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        NoteText(
            stringResource(if (targetKcal != null) R.string.progress_stats_week_note_target else R.string.progress_stats_week_note),
        )
    }
}

/**
 * A bar per day, Monday to Sunday: finished days filled, today outlined (it fills up as you log),
 * days still to come and days with nothing logged as a thin stub. The dashed line is the target.
 */
@Composable
private fun WeekBars(week: WeekStats, targetKcal: Int?) {
    val accent = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(
        R.string.progress_stats_week_desc,
        week.days.filter { it.logged }.joinToString { "${it.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${Format.kcal(it.kcal)}" },
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .semantics { contentDescription = description },
    ) {
        val gap = 10.dp.toPx()
        val barWidth = (size.width - gap * 6) / 7
        val top = 6.dp.toPx()
        val scale = max(max(week.days.maxOf { it.kcal }, (targetKcal ?: 0).toDouble()), 1.0)
        fun heightFor(kcal: Double) = ((kcal / scale) * (size.height - top)).toFloat()
        val stub = 4.dp.toPx()
        val corner = CornerRadius(6.dp.toPx())

        week.days.forEachIndexed { i, day ->
            val left = i * (barWidth + gap)
            val h = heightFor(day.kcal)
            when {
                !day.logged || h < stub -> drawRoundRect(
                    color = FoodColors.AccentTrack,
                    topLeft = Offset(left, size.height - stub),
                    size = Size(barWidth, stub),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
                day.kind == DayKind.TODAY -> {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left + stroke / 2, size.height - h + stroke / 2),
                        size = Size(barWidth - stroke, h - stroke / 2),
                        cornerRadius = corner,
                        style = Stroke(width = stroke),
                    )
                }
                else -> drawRoundRect(
                    color = accent,
                    topLeft = Offset(left, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = corner,
                )
            }
        }
        if (targetKcal != null && targetKcal > 0) {
            val y = size.height - heightFor(targetKcal.toDouble())
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

@Composable
private fun ByMonthCard(months: List<MonthAverage>) {
    val accent = MaterialTheme.colorScheme.primary
    val scale = max(months.maxOf { it.averageKcal ?: 0.0 }, 1.0)
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            SectionTitle(stringResource(R.string.progress_stats_by_month))
            NoteText(stringResource(R.string.progress_stats_per_day))
        }
        Row(
            Modifier.fillMaxWidth().height(128.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEach { m ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        m.averageKcal?.let { Format.kcal(it) } ?: stringResource(R.string.progress_stats_no_value),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    val fraction = ((m.averageKcal ?: 0.0) / scale).toFloat()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(max(100f * fraction, 4f).dp)
                            .background(
                                color = when {
                                    m.averageKcal == null -> FoodColors.AccentTrack
                                    m.isCurrent -> FoodColors.AccentOutline
                                    else -> accent
                                },
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            months.forEach { m ->
                Text(
                    Format.monthShort(m.month),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (m.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (m.isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        months.lastOrNull { !it.isCurrent }?.let { last ->
            NoteText(stringResource(R.string.progress_stats_days_logged, Format.monthFull(last.month), last.daysLogged, last.daysSoFar))
        }
    }
}

@Composable
private fun DrinksCard(week: WeekStats) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(stringResource(R.string.progress_stats_drinks_title))
                NoteText(
                    when (week.topDrinks.size) {
                        0 -> stringResource(R.string.progress_stats_drinks_none)
                        1 -> stringResource(R.string.progress_stats_drinks_one, week.drinksPercent, week.topDrinks[0])
                        else -> stringResource(R.string.progress_stats_drinks_two, week.drinksPercent, week.topDrinks[0], week.topDrinks[1])
                    },
                )
            }
            Text(
                stringResource(R.string.progress_kcal, Format.kcal(week.drinksKcal)),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}
