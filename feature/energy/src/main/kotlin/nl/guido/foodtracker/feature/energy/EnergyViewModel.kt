package nl.guido.foodtracker.feature.energy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.model.WeighIn
import javax.inject.Inject

@HiltViewModel
internal class EnergyViewModel @Inject constructor(
    private val data: EnergyData,
    private val today: Today,
) : ViewModel() {

    /** Null while loading. */
    val state: StateFlow<EnergyState?> = data.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves the form if it is complete. Returns false (and saves nothing) when something is missing. */
    fun saveProfile(form: ProfileForm, onSaved: () -> Unit): Boolean {
        val current = state.value ?: return false
        val profile = form.toProfile(current.user.userId, current.user.displayName, today.date(), current.profile)
            ?: return false
        viewModelScope.launch {
            data.saveProfile(profile, isNew = current.profile == null)
            onSaved()
        }
        return true
    }

    fun saveWeighIn(kg: Double, onSaved: (WeighIn) -> Unit) {
        viewModelScope.launch { onSaved(data.saveWeighIn(kg)) }
    }

    fun changeWeighIn(id: String, kg: Double, onSaved: () -> Unit) {
        viewModelScope.launch {
            data.changeWeighIn(id, kg)
            onSaved()
        }
    }

    fun deleteWeighIn(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            data.deleteWeighIn(id)
            onDeleted()
        }
    }

    fun today() = today.date()
}
