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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.EnergyRepository
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.WeeklyReview
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    /** Something the camera scanned and weighed, logged straight away. */
    data class Logged(val name: String, val meal: Meal, val id: Id) : TodayEvent
    data class Open(val route: String) : TodayEvent
}

/** The date and the meal that fits the time; changes a few times a day. */
private data class Moment(val date: LocalDate, val meal: Meal)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class TodayViewModel @Inject constructor(
    private val session: SessionRepository,
    private val diary: DiaryRepository,
    private val foods: FoodRepository,
    private val energy: EnergyRepository,
    private val reviewPrefs: ReviewPrefs,
) : ViewModel() {

    private val moment: Flow<Moment> = flow {
        while (true) {
            emit(Moment(LocalDate.now(), mealForTime(LocalTime.now())))
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val events = Channel<TodayEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TodayEvent> = events.receiveAsFlow()

    val state: StateFlow<TodayUiState?> = combine(session.currentUser, moment) { user, now -> user to now }
        .flatMapLatest { (user, now) ->
            combine(
                diary.entries(user.userId, now.date),
                diary.entriesBetween(user.userId, now.date.minusDays(HISTORY_DAYS), now.date.minusDays(1)),
                energy.target.map { it?.targetKcal },
                energy.weeklyReview,
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
                is TodayEvent.Logged -> diary.delete(event.id)
                is TodayEvent.Open -> Unit
            }
        }
    }

    fun dismissReview(review: WeeklyReview) = reviewPrefs.dismiss(review.weekStart)

    /** True while the camera was opened from Drinks, so what comes back is logged as a drink. */
    private var cameraForDrink = false

    fun openingCamera(forDrink: Boolean) {
        cameraForDrink = forDrink
    }

    /**
     * The camera came back. A scanned food already has its amount (the camera asked "How much?"),
     * so it is logged here at once, with Undo, in the meal that fits (Drinks for a drink).
     * A weight on its own continues in Log food, to pick what was weighed.
     */
    fun onCameraResult(foodId: Id?, grams: Double?) {
        if (foodId == null && grams == null) return
        val forDrink = cameraForDrink
        cameraForDrink = false
        viewModelScope.launch {
            val food = foodId?.let { foods.get(it) }
            val meal = mealForCameraResult(food?.isDrink ?: false, forDrink, LocalTime.now())
            if (food != null && grams != null && grams > 0) {
                val line = cameraEntry(food, grams, session.currentUser.value.userId, LocalDate.now(), meal, Instant.now())
                diary.save(line)
                events.send(TodayEvent.Logged(line.displayName, meal, line.id))
            } else {
                events.send(TodayEvent.Open(TodayRoutes.logFood(meal, foodId, grams)))
            }
        }
    }

    private companion object {
        const val HISTORY_DAYS = 28L
    }
}
