package nl.guido.foodtracker.feature.progress.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import nl.guido.foodtracker.core.data.repo.ProfileRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import java.time.LocalDate
import javax.inject.Inject

internal sealed interface WeightUiState {
    data object Loading : WeightUiState
    /** No profile yet, so no start or target weight to show progress against. */
    data object NoProfile : WeightUiState
    data class Ready(val summary: WeightSummary, val today: LocalDate) : WeightUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class WeightViewModel @Inject constructor(
    session: SessionRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<WeightUiState> = session.currentUser
        .flatMapLatest { user ->
            combine(profiles.profile(user.userId), profiles.weighIns(user.userId)) { profile, weighIns ->
                if (profile == null) {
                    WeightUiState.NoProfile
                } else {
                    WeightUiState.Ready(WeightMath.summary(profile, weighIns), LocalDate.now())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState.Loading)
}
