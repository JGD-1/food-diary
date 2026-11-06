package nl.guido.foodtracker.feature.sync.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import nl.guido.foodtracker.core.data.repo.SharedDayRepository
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.EnergyRepository
import nl.guido.foodtracker.core.model.SharedDay
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.feature.sync.local.SyncPrefs
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * "Show my day to my partner" (round 10): while switched on, the kcal eaten per day (and the
 * person's own target) goes to the household before each sync. Nothing else from the diary.
 * Switching it off deletes every own day total, so the partner's phone stops showing them.
 */
@Singleton
class DayShare @Inject constructor(
    private val prefs: SyncPrefs,
    private val diary: DiaryRepository,
    // A Provider: the energy stream reads the session, which this module provides, so a direct
    // dependency would be a loop (SyncManager → DayShare → EnergyRepository → session → SyncManager).
    private val energy: Provider<EnergyRepository>,
    private val totals: SharedDayRepository,
) {
    private val _enabled = MutableStateFlow(prefs.shareDay)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    suspend fun setEnabled(userId: Id, on: Boolean) {
        prefs.shareDay = on
        _enabled.value = on
        if (on) refresh(userId) else totals.deleteAllOwn(userId)
    }

    /** Brings the last [DAYS] days up to date; unchanged days are not saved again. */
    suspend fun refresh(userId: Id, today: LocalDate = LocalDate.now()) {
        if (!prefs.shareDay) return
        val from = today.minusDays(DAYS - 1L)
        val entries = diary.entriesBetween(userId, from, today).first()
        val target = energy.get().target.first()?.targetKcal
        dayTotals(userId, entries, target, from, today).forEach { totals.saveOwn(it) }
    }

    companion object {
        /** Days kept up to date: a week, so changes to yesterday's diary reach the partner too. */
        const val DAYS = 7
    }
}

/** One total for every day from [from] to [to], 0 kcal on days without entries (so a removed meal also counts). */
internal fun dayTotals(userId: Id, entries: List<LogEntry>, targetKcal: Int?, from: LocalDate, to: LocalDate): List<SharedDay> {
    val byDay = entries.filter { it.userId == userId }.groupBy { it.date }
    return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { day ->
        SharedDay(userId, day, byDay[day].orEmpty().sumOf { it.nutrients.kcal }.roundToInt(), targetKcal)
    }.toList()
}
