package nl.guido.foodtracker.feature.today

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.EnergyRepository
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.ProfileRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.WeeklyReview
import nl.guido.foodtracker.core.ui.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

internal data class TodayUiState(
    /** The day being viewed; logging from this screen goes to this day. */
    val date: LocalDate,
    val today: LocalDate,
    val displayName: String,
    val summary: TodaySummary,
    /** Optional daily protein goal from the profile, shown on the macro line. */
    val proteinGoalG: Int?,
    val review: WeeklyReview?,
) {
    val isToday: Boolean get() = date == today
    val canGoForward: Boolean get() = nextDay(date, today) != null
    /** For routes: null when viewing today, so plain routes keep working. */
    val dateArg: LocalDate? get() = routeDate(date, today)
}

/** One-off things the screen reacts to: a message with Undo, or opening another screen. */
internal sealed interface TodayEvent {
    data class Added(val meal: Meal, val count: Int, val ids: List<Id>) : TodayEvent
    /** [portion] is the shared pot portion that went with a batch line, put back on Undo. */
    data class Removed(val entry: LogEntry, val portion: BatchPortion? = null) : TodayEvent
    /** A meal's lines moved to another meal; [before] puts them back. */
    data class Moved(val to: Meal, val before: List<LogEntry>) : TodayEvent
    /** Lines copied from an earlier day to today. */
    data class Copied(val ids: List<Id>) : TodayEvent
    /** Something the camera scanned and weighed, logged straight away. */
    data class Logged(val name: String, val meal: Meal, val id: Id) : TodayEvent
    data class Open(val route: String) : TodayEvent
}

private data class Goals(val targetKcal: Int?, val proteinG: Int?)

/** The date and the meal that fits the time; changes a few times a day. */
private data class Moment(val date: LocalDate, val meal: Meal)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class TodayViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val diary: DiaryRepository,
    private val foods: FoodRepository,
    private val energy: EnergyRepository,
    private val profiles: ProfileRepository,
    private val recipes: RecipeRepository,
    private val reviewPrefs: ReviewPrefs,
) : ViewModel() {

    private val moment: Flow<Moment> = flow {
        while (true) {
            emit(Moment(LocalDate.now(), mealForTime(LocalTime.now())))
            delay(60_000)
        }
    }.distinctUntilChanged()

    /**
     * The day picked with the ‹ › arrows, or the day opened from Stats (day/{date}).
     * Null = follow today, so the screen moves on by itself after midnight.
     */
    private val pickedDay = savedState.getStateFlow<String?>(PICKED_DAY, savedState.get<String>(Routes.ARG_DATE))
        .map { parseDate(it) }

    private val events = Channel<TodayEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TodayEvent> = events.receiveAsFlow()

    val state: StateFlow<TodayUiState?> = combine(session.currentUser, moment, pickedDay) { user, now, picked ->
        Triple(user, now, picked?.takeIf { it < now.date } ?: now.date)
    }
        .flatMapLatest { (user, now, day) ->
            combine(
                diary.entries(user.userId, day),
                diary.entriesBetween(user.userId, day.minusDays(HISTORY_DAYS), day.minusDays(1)),
                combine(energy.target, profiles.profile(user.userId)) { target, profile ->
                    Goals(target?.targetKcal, profile?.proteinGoalG)
                },
                energy.weeklyReview,
                reviewPrefs.dismissedOn,
            ) { todayEntries, days, goals, weekly, dismissedWeek ->
                val isToday = day == now.date
                TodayUiState(
                    date = day,
                    today = now.date,
                    displayName = user.displayName,
                    summary = todaySummary(todayEntries, days, goals.targetKcal, if (isToday) now.meal else null),
                    proteinGoalG = goals.proteinG,
                    review = if (isToday) reviewToShow(now.date, weekly, dismissedWeek) else null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shows another day: earlier days only, today is the last one. */
    fun showDay(date: LocalDate) {
        val today = LocalDate.now()
        savedState[PICKED_DAY] = date.takeIf { it < today }?.toString() ?: TODAY_MARK
    }

    /** Moves all lines of [from] on the viewed day to [to], with Undo. */
    fun move(from: Meal, to: Meal) {
        val current = state.value ?: return
        val lines = mealEntries(current.summary, from)
        if (lines.isEmpty() || from == to) return
        viewModelScope.launch {
            moveEntries(lines, to).forEach { diary.save(it) }
            events.send(TodayEvent.Moved(to, lines))
        }
    }

    /** "Copy to today": logs an earlier day's meal again today, in the same meal, with Undo. */
    fun copyToToday(meal: Meal) {
        val current = state.value ?: return
        if (current.isToday) return
        val copies = copyEntries(mealEntries(current.summary, meal), LocalDate.now(), Instant.now())
        if (copies.isEmpty()) return
        viewModelScope.launch {
            copies.forEach { diary.save(it) }
            events.send(TodayEvent.Copied(copies.map { it.id }))
        }
    }

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
        viewModelScope.launch {
            diary.save(withGrams(entry, grams))
            // The pot shows grams left for the household, so the shared portion follows the new amount.
            potPortion(entry)?.let { recipes.savePortion(it.copy(grams = grams)) }
        }
    }

    fun remove(entry: LogEntry) {
        viewModelScope.launch {
            diary.delete(entry.id)
            // A removed batch line gives its grams back to the pot, or the pot looks emptier than it is.
            val portion = potPortion(entry)
            portion?.let { recipes.deletePortion(it.id) }
            events.send(TodayEvent.Removed(entry, portion))
        }
    }

    /** The shared pot portion saved with a batch line (by its diary line id), if any. */
    private suspend fun potPortion(entry: LogEntry): BatchPortion? {
        val batch = entry.what as? Logged.BatchShare ?: return null
        return portionFor(recipes.portions(batch.batchId).first(), entry.id)
    }

    fun undo(event: TodayEvent) {
        viewModelScope.launch {
            when (event) {
                is TodayEvent.Added -> event.ids.forEach { diary.delete(it) }
                is TodayEvent.Removed -> {
                    diary.save(event.entry)
                    event.portion?.let { recipes.savePortion(it) }
                }
                is TodayEvent.Logged -> diary.delete(event.id)
                is TodayEvent.Moved -> event.before.forEach { diary.save(it) }
                is TodayEvent.Copied -> event.ids.forEach { diary.delete(it) }
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
        val today = LocalDate.now()
        val day = state.value?.date ?: today
        viewModelScope.launch {
            val food = foodId?.let { foods.get(it) }
            val meal = mealForCameraResult(food?.isDrink ?: false, forDrink, LocalTime.now())
            if (food != null && grams != null && grams > 0) {
                val line = cameraEntry(food, grams, session.currentUser.value.userId, day, meal, Instant.now())
                diary.save(line)
                events.send(TodayEvent.Logged(line.displayName, meal, line.id))
            } else {
                events.send(TodayEvent.Open(TodayRoutes.logFood(meal, foodId, grams, routeDate(day, today))))
            }
        }
    }

    private companion object {
        const val HISTORY_DAYS = 28L
        const val PICKED_DAY = "today_picked_day"
        /** Stored when the arrows come back to today, so a day opened from Stats isn't used again. */
        const val TODAY_MARK = "today"
    }
}
