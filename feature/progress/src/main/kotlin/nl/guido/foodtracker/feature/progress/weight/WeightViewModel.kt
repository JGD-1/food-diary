package nl.guido.foodtracker.feature.progress.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.ProfileRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.WeighIn
import nl.guido.foodtracker.core.model.newId
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
    private val session: SessionRepository,
    private val profiles: ProfileRepository,
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

    /**
     * STAND-IN weigh-in entry, used until stream 4's weigh-in screen has a route.
     * One weigh-in per day: a second one today replaces the first.
     */
    fun logWeighIn(kg: Double) {
        viewModelScope.launch {
            val userId = session.currentUser.value.userId
            val today = LocalDate.now()
            val existing = profiles.weighIns(userId).first().firstOrNull { it.date == today }
            profiles.saveWeighIn(WeighIn(existing?.id ?: newId(), userId, today, kg))
        }
    }
}
