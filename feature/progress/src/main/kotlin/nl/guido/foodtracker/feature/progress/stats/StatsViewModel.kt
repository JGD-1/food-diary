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
import nl.guido.foodtracker.core.data.repo.ProfileRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.EnergyEstimator
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
    profiles: ProfileRepository,
    energy: EnergyEstimator,
) : ViewModel() {

    val state: StateFlow<StatsUiState> = session.currentUser
        .flatMapLatest { user ->
            val today = LocalDate.now()
            val entries = diary.entriesBetween(
                user.userId,
                StatsMath.firstDayNeeded(today),
                StatsMath.weekStart(today).plusDays(6),
            )
            combine(entries, profiles.profile(user.userId), profiles.weighIns(user.userId)) { list, profile, weighIns ->
                StatsUiState.Ready(
                    week = StatsMath.week(list, today),
                    months = StatsMath.months(list, today),
                    targetKcal = profile?.let { energy.dailyTarget(it, weighIns, StatsMath.intake(list)).targetKcal },
                    today = today,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)
}
