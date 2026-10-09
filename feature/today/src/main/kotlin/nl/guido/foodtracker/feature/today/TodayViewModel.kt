package nl.guido.foodtracker.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.EnergyRepository
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.ProfileRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.EnergyEstimator
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.WeeklyReview
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import javax.inject.Inject

internal data class TodayUiState(
    val date: LocalDate,
    val displayName: String,
    val summary: TodaySummary,
    val review: WeeklyReview?,
)

/** One-off things the screen reacts to: a message with Undo, or opening another screen. */
internal sealed interface TodayEvent {
    data class Added(val meal: Meal, val count: Int, val ids: List<Id>) : TodayEvent
    data class Removed(val entry: LogEntry) : TodayEvent
    data class Open(val route: String) : TodayEvent
}

/** The date and the meal that fits the time; changes a few times a day. */
private data class Moment(val date: LocalDate, val meal: Meal)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class TodayViewModel @Inject constructor(
    session: SessionRepository,
    private val diary: DiaryRepository,
    private val foods: FoodRepository,
    profiles: ProfileRepository,
    private val energy: EnergyEstimator,
    private val reviewPrefs: ReviewPrefs,
    energyRepository: Optional<EnergyRepository>,
) : ViewModel() {

    private val moment: Flow<Moment> = flow {
        while (true) {
            emit(Moment(LocalDate.now(), mealForTime(LocalTime.now())))
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val events = Channel<TodayEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TodayEvent> = events.receiveAsFlow()

    private val energyRepo: EnergyRepository? = energyRepository.orElse(null)

    val state: StateFlow<TodayUiState?> = combine(session.currentUser, moment) { user, now -> user to now }
        .flatMapLatest { (user, now) ->
            val history = diary.entriesBetween(user.userId, now.date.minusDays(HISTORY_DAYS), now.date.minusDays(1))
            // The energy part provides the target and Monday review; until it is in the app,
            // the target is worked out here from the profile and the review stays hidden.
            val target: Flow<Int?> = energyRepo?.target?.map { it?.targetKcal }
                ?: combine(profiles.profile(user.userId), profiles.weighIns(user.userId), history) { profile, weighIns, days ->
                    profile?.let { energy.dailyTarget(it, weighIns, dayTotals(days)).targetKcal }
                }
            val review: Flow<WeeklyReview?> = energyRepo?.weeklyReview ?: flowOf(null)
            combine(
                diary.entries(user.userId, now.date),
                history,
                target,
                review,
                reviewPrefs.dismissedOn,
            ) { todayEntries, days, targetKcal, weekly, dismissedWeek ->
                TodayUiState(
                    date = now.date,
                    displayName = user.displayName,
                    summary = todaySummary(todayEntries, days, targetKcal, now.meal),
                    review = reviewToShow(now.date, weekly, dismissedWeek),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Again": logs the same items once more (or last time's, for an empty meal). */
    fun again(meal: Meal) {
        val current = state.value ?: return
        val copies = copyEntries(againSource(current.summary, meal), current.date, Instant.now(), meal)
        if (copies.isEmpty()) return
        viewModelScope.launch {
            copies.forEach { diary.save(it) }
            events.send(TodayEvent.Added(meal, copies.size, copies.map { it.id }))
        }
    }

    fun changeAmount(entry: LogEntry, grams: Double) {
        if (!canChangeAmount(entry)) return
        viewModelScope.launch { diary.save(withGrams(entry, grams)) }
    }

    fun remove(entry: LogEntry) {
        viewModelScope.launch {
            diary.delete(entry.id)
            events.send(TodayEvent.Removed(entry))
        }
    }

    fun undo(event: TodayEvent) {
        viewModelScope.launch {
            when (event) {
                is TodayEvent.Added -> event.ids.forEach { diary.delete(it) }
                is TodayEvent.Removed -> diary.save(event.entry)
                is TodayEvent.Open -> Unit
            }
        }
    }

    fun dismissReview(review: WeeklyReview) = reviewPrefs.dismiss(review.weekStart)

    /** The camera came back with a food (Scan) and/or a weight (Weigh): continue in Log food. */
    fun onCameraResult(foodId: Id?, grams: Double?) {
        if (foodId == null && grams == null) return
        viewModelScope.launch {
            val isDrink = foodId?.let { foods.get(it)?.isDrink } ?: false
            val meal = if (isDrink) Meal.DRINKS else mealForTime(LocalTime.now())
            events.send(TodayEvent.Open(TodayRoutes.logFood(meal, foodId, grams)))
        }
    }

    private companion object {
        const val HISTORY_DAYS = 28L
    }
}
