package nl.guido.foodtracker.feature.progress.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.EnergyRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import java.time.LocalDate
import javax.inject.Inject

internal sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Ready(
        val week: WeekStats,
        val months: List<MonthAverage>,
        /** Daily kcal target, or null until a profile exists. */
        val targetKcal: Int?,
        val today: LocalDate,
    ) : StatsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class StatsViewModel @Inject constructor(
    session: SessionRepository,
    diary: DiaryRepository,
    energy: EnergyRepository,
) : ViewModel() {

    val state: StateFlow<StatsUiState> = session.currentUser
        .flatMapLatest { user ->
            val today = LocalDate.now()
            val entries = diary.entriesBetween(
                user.userId,
                StatsMath.firstDayNeeded(today),
                StatsMath.weekStart(today).plusDays(6),
            )
            // The same daily target as Today and the target screen (stream 4), null until there's a profile.
            combine(entries, energy.target) { list, target ->
                StatsUiState.Ready(
                    week = StatsMath.week(list, today),
                    months = StatsMath.months(list, today),
                    targetKcal = target?.targetKcal,
                    today = today,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)
}
