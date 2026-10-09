package nl.guido.foodtracker.feature.progress.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.feature.progress.Format
import nl.guido.foodtracker.feature.progress.NoteText
import nl.guido.foodtracker.feature.progress.ProgressTrack
import nl.guido.foodtracker.feature.progress.R
import nl.guido.foodtracker.feature.progress.SectionCard
import nl.guido.foodtracker.feature.progress.SectionTitle
import nl.guido.foodtracker.feature.progress.TabHeader
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun WeightRoute(
    onLogWeighIn: () -> Unit,
    onSetTarget: () -> Unit,
    viewModel: WeightViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WeightScreen(state = state, onLogWeighIn = onLogWeighIn, onSetTarget = onSetTarget)
}

@Composable
internal fun WeightScreen(
    state: WeightUiState,
    onLogWeighIn: () -> Unit,
    onSetTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The cards scroll (large text sizes need room); the button stays at the bottom.
    Column(modifier = modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val day = (state as? WeightUiState.Ready)?.summary?.weighInDay
            TabHeader(
                kicker = if (day != null) {
                    stringResource(R.string.progress_weight_every, day.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                } else {
                    stringResource(R.string.progress_weight_weekly)
                },
                title = stringResource(R.string.progress_weight_title),
            )
            when (state) {
                WeightUiState.Loading -> Unit
                WeightUiState.NoProfile -> NoProfileCard(onSetTarget)
                is WeightUiState.Ready -> {
                    ProgressCard(state.summary.progress)
                    OverTimeCard(state.summary)
                    WeekToWeekCard(state.summary, state.today)
                }
            }
        }
        Button(onClick = onLogWeighIn, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.progress_weight_log), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun NoProfileCard(onSetTarget: () -> Unit) {
    SectionCard {
        SectionTitle(stringResource(R.string.progress_weight_no_profile_title))
        NoteText(stringResource(R.string.progress_weight_no_profile_body))
        OutlinedButton(onClick = onSetTarget, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.progress_weight_set_target))
        }
    }
}

@Composable
private fun ProgressCard(progress: WeightProgress) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.progress_kg, Format.kg(progress.currentKg)),
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 40.sp,
            )
            Text(
                if (progress.reached) {
                    stringResource(R.string.progress_weight_reached, Format.kg(progress.targetKg))
                } else {
                    stringResource(R.string.progress_weight_to_go, Format.kg(progress.toGoKg), Format.kg(progress.targetKg))
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        ProgressTrack(progress.fraction.toFloat())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NoteText(
                if (progress.startDate != null) {
                    stringResource(R.string.progress_weight_start_on, Format.kg(progress.startKg), Format.dayMonth(progress.startDate))
                } else {
                    stringResource(R.string.progress_weight_start, Format.kg(progress.startKg))
                },
            )
            val change = progress.currentKg - progress.startKg
            NoteText(
                when {
                    Format.kg(kotlin.math.abs(change)) == Format.kg(0.0) -> stringResource(R.string.progress_weight_same)
                    change < 0 -> stringResource(R.string.progress_weight_down, Format.kg(-change))
                    else -> stringResource(R.string.progress_weight_up, Format.kg(change))
                },
            )
        }
    }
}

@Composable
private fun OverTimeCard(summary: WeightSummary) {
    SectionCard {
        SectionTitle(stringResource(R.string.progress_weight_over_time))
        val target = Format.kg(summary.progress.targetKg)
        val description = if (summary.points.isEmpty()) {
            stringResource(R.string.progress_weight_graph_empty)
        } else {
            stringResource(
                R.string.progress_weight_graph_desc,
                Format.kg(summary.points.first().kg),
                Format.kg(summary.points.last().kg),
                target,
            )
        }
        WeightLineChart(
            points = summary.points,
            targetKg = summary.progress.targetKg,
            goalLabel = stringResource(R.string.progress_weight_goal, target),
            description = description,
        )
        if (summary.points.isEmpty()) NoteText(description)
    }
}

@Composable
private fun WeekToWeekCard(summary: WeightSummary, today: LocalDate) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionTitle(stringResource(R.string.progress_weight_week_to_week))
            summary.changes.lastOrNull()?.let {
                NoteText(stringResource(R.string.progress_weight_last_change, Format.kgSigned(it.deltaKg)))
            }
        }
        val latest = summary.changes.lastOrNull()
        WeekChangeBars(
            changes = summary.changes,
            description = if (latest != null) {
                stringResource(R.string.progress_weight_changes_desc, summary.changes.size, Format.kgSigned(latest.deltaKg))
            } else {
                stringResource(R.string.progress_weight_changes_empty)
            },
        )
        NoteText(trendText(summary, today))
    }
}

@Composable
private fun trendText(summary: WeightSummary, today: LocalDate): String {
    val progress = summary.progress
    val losing = progress.targetKg < progress.startKg
    return when (val trend = summary.trend) {
        is TrendNote.NotEnoughData ->
            pluralStringResource(R.plurals.progress_weight_trend_not_enough, trend.weighInsNeeded, trend.weighInsNeeded)
        TrendNote.Reached -> stringResource(R.string.progress_weight_trend_reached)
        TrendNote.Steady -> stringResource(R.string.progress_weight_trend_steady)
        is TrendNote.Toward -> stringResource(
            if (losing) R.string.progress_weight_trend_losing else R.string.progress_weight_trend_gaining,
            Format.kgSmall(trend.kgPerWeek),
            Format.kg(progress.targetKg),
            Format.monthHint(trend.eta, today),
        )
        is TrendNote.Away -> stringResource(
            if (losing) R.string.progress_weight_trend_away_up else R.string.progress_weight_trend_away_down,
            Format.kgSmall(trend.kgPerWeek),
        )
    }
}
