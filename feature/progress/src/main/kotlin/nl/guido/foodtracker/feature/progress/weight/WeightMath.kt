package nl.guido.foodtracker.feature.progress.weight

import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil

/** One point on the "Over time" graph: the last weigh-in of a week (weeks start on Monday). */
internal data class WeekPoint(val weekStart: LocalDate, val date: LocalDate, val kg: Double)

/** One bar on the "Week to week" chart: the change since the previous weigh-in. */
internal data class WeekChange(val date: LocalDate, val deltaKg: Double, val towardTarget: Boolean)

/** Where you are between your start weight and your target weight. */
internal data class WeightProgress(
    val startKg: Double,
    val targetKg: Double,
    val currentKg: Double,
    /** Date of the first weigh-in, if there is one. */
    val startDate: LocalDate?,
    /** How far from the target, always 0 or more. */
    val toGoKg: Double,
    /** Change since the start, positive when it went the way of the target. */
    val movedKg: Double,
    /** 0 at the start, 1 at the target. */
    val fraction: Double,
    val reached: Boolean,
)

/** The plain-language note under the week-to-week chart. Neutral in every case. */
internal sealed interface TrendNote {
    /** Fewer than [MIN_WEIGH_INS_FOR_TREND] weekly weigh-ins so far. */
    data class NotEnoughData(val weighInsNeeded: Int) : TrendNote
    data object Reached : TrendNote
    /** Moving toward the target at [kgPerWeek]; reaches it around [eta]. */
    data class Toward(val kgPerWeek: Double, val eta: YearMonth) : TrendNote
    /** Changing less than [STEADY_KG_PER_WEEK] a week, or so slowly the target is years away. */
    data object Steady : TrendNote
    /** Moving away from the target lately (e.g. up while aiming to go down). */
    data class Away(val kgPerWeek: Double) : TrendNote
}

internal data class WeightSummary(
    val progress: WeightProgress,
    /** Weekly points for the graph, oldest first, at most [GRAPH_WEEKS]. */
    val points: List<WeekPoint>,
    /** Week-to-week changes, oldest first, at most [CHANGE_BARS]. */
    val changes: List<WeekChange>,
    val trend: TrendNote,
    /** The weekday of the latest weigh-in, used for "Weigh-in every Sunday". */
    val weighInDay: DayOfWeek?,
)

internal const val MIN_WEIGH_INS_FOR_TREND = 3
internal const val STEADY_KG_PER_WEEK = 0.05
internal const val GRAPH_WEEKS = 26
internal const val CHANGE_BARS = 11
/** The trend looks at the last 12 weeks, so an old phase doesn't hide what's happening now. */
internal const val TREND_WINDOW_DAYS = 84L
/** Further away than this, a date would mean nothing: the note says "steady" instead. */
internal const val MAX_ETA_WEEKS = 104

internal object WeightMath {

    /** Reads a typed weight in kg; accepts "78.4" and "78,4". Null if it isn't a plausible body weight. */
    fun parseKg(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** One point per week: the last weigh-in of that week. Extra weigh-ins in a week are ignored. */
    fun weeklyPoints(weighIns: List<WeighIn>): List<WeekPoint> =
        weighIns.groupBy { weekStart(it.date) }
            .map { (start, list) ->
                val last = list.maxBy { it.date }
                WeekPoint(start, last.date, last.kg)
            }
            .sortedBy { it.weekStart }

    /** +1 when the target is above the start weight (gaining), -1 otherwise (losing). */
    private fun direction(startKg: Double, targetKg: Double): Int = if (targetKg > startKg) 1 else -1

    fun progress(profile: UserProfile, points: List<WeekPoint>): WeightProgress {
        val start = profile.startWeightKg
        val target = profile.targetWeightKg
        val current = points.lastOrNull()?.kg ?: start
        val dir = direction(start, target)
        val moved = (current - start) * dir
        val total = abs(target - start)
        val reached = (current - target) * dir >= 0
        val fraction = when {
            reached -> 1.0
            total == 0.0 -> 1.0
            else -> (moved / total).coerceIn(0.0, 1.0)
        }
        return WeightProgress(
            startKg = start,
            targetKg = target,
            currentKg = current,
            startDate = points.firstOrNull()?.date,
            toGoKg = if (reached) 0.0 else abs(target - current),
            movedKg = moved,
            fraction = fraction,
            reached = reached,
        )
    }

    fun changes(points: List<WeekPoint>, startKg: Double, targetKg: Double): List<WeekChange> {
        val dir = direction(startKg, targetKg)
        return points.zipWithNext { a, b ->
            val delta = b.kg - a.kg
            WeekChange(b.date, delta, delta * dir > 0)
        }
    }

    /**
     * Average change per week (least-squares line through the recent weekly points).
     * Null with fewer than [MIN_WEIGH_INS_FOR_TREND] points in the window.
     */
    fun kgPerWeek(points: List<WeekPoint>): Double? {
        val latest = points.lastOrNull() ?: return null
        val recent = points.filter { ChronoUnit.DAYS.between(it.date, latest.date) <= TREND_WINDOW_DAYS }
        if (recent.size < MIN_WEIGH_INS_FOR_TREND) return null
        val origin = recent.first().date
        val xs = recent.map { ChronoUnit.DAYS.between(origin, it.date).toDouble() }
        val ys = recent.map { it.kg }
        val meanX = xs.average()
        val meanY = ys.average()
        val sxx = xs.sumOf { (it - meanX) * (it - meanX) }
        if (sxx == 0.0) return null
        val sxy = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
        return sxy / sxx * 7.0
    }

    fun trend(points: List<WeekPoint>, progress: WeightProgress): TrendNote {
        if (points.size < MIN_WEIGH_INS_FOR_TREND) {
            return TrendNote.NotEnoughData(MIN_WEIGH_INS_FOR_TREND - points.size)
        }
        if (progress.reached) return TrendNote.Reached
        val slope = kgPerWeek(points) ?: return TrendNote.NotEnoughData(1)
        if (abs(slope) < STEADY_KG_PER_WEEK) return TrendNote.Steady
        val dir = direction(progress.startKg, progress.targetKg)
        if (slope * dir < 0) return TrendNote.Away(abs(slope))
        val weeks = ceil(progress.toGoKg / abs(slope)).toLong()
        if (weeks > MAX_ETA_WEEKS) return TrendNote.Steady
        val eta = YearMonth.from(points.last().date.plusWeeks(weeks))
        return TrendNote.Toward(abs(slope), eta)
    }

    fun summary(profile: UserProfile, weighIns: List<WeighIn>): WeightSummary {
        val points = weeklyPoints(weighIns)
        val progress = progress(profile, points)
        return WeightSummary(
            progress = progress,
            points = points.takeLast(GRAPH_WEEKS),
            changes = changes(points, profile.startWeightKg, profile.targetWeightKg).takeLast(CHANGE_BARS),
            trend = trend(points, progress),
            weighInDay = weighIns.maxByOrNull { it.date }?.date?.dayOfWeek,
        )
    }
}
