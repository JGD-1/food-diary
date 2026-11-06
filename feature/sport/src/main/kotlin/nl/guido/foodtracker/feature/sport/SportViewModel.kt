package nl.guido.foodtracker.feature.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.data.repo.SportRepository
import nl.guido.foodtracker.core.model.SportEntry
import nl.guido.foodtracker.core.model.newId
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SportViewModel @Inject constructor(
    private val session: SessionRepository,
    private val sports: SportRepository,
) : ViewModel() {

    val state: StateFlow<SportDays?> = session.currentUser.flatMapLatest { user ->
        val today = LocalDate.now()
        sports.between(user.userId, SportLogic.firstDayLoaded(today), today).map { SportLogic.days(it, today) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves a new line for today, or the changes to [editing]. */
    fun save(form: SportForm, editing: SportEntry?) {
        val kcal = form.kcalValue ?: return
        if (!form.canSave) return
        viewModelScope.launch {
            val entry = editing?.copy(name = form.cleanName, kcal = kcal, minutes = form.minutesValue)
                ?: SportEntry(
                    id = newId(),
                    userId = session.currentUser.value.userId,
                    date = LocalDate.now(),
                    name = form.cleanName,
                    kcal = kcal,
                    createdAt = Instant.now(),
                    minutes = form.minutesValue,
                )
            sports.save(entry)
        }
    }

    fun delete(entry: SportEntry) {
        viewModelScope.launch { sports.delete(entry.id) }
    }

    /** Undo after delete: the same line comes back. */
    fun restore(entry: SportEntry) {
        viewModelScope.launch { sports.save(entry) }
    }
}
