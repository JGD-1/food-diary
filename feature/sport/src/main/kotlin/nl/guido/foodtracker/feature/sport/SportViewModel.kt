package nl.guido.foodtracker.feature.sport

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.ProfileRepository
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
    private val healthConnect: HealthConnectSport,
    profiles: ProfileRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Round 17 (P11): the activity-level question shows once, in the first Add sport, then never again. */
    private val _askActivity = MutableStateFlow(!prefs.getBoolean(KEY_ACTIVITY_ASKED, false))
    val askActivity: StateFlow<Boolean> = _askActivity.asStateFlow()

    fun activityAsked() {
        prefs.edit().putBoolean(KEY_ACTIVITY_ASKED, true).apply()
        _askActivity.value = false
    }

    /** Body weight for the kcal estimate: the latest weigh-in, else the profile's start weight. */
    val weightKg: StateFlow<Double?> = session.currentUser.flatMapLatest { user ->
        combine(profiles.profile(user.userId), profiles.weighIns(user.userId)) { profile, weighIns ->
            weighIns.maxByOrNull { it.date }?.kg ?: profile?.startWeightKg
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reading sport from other apps through Health Connect (round 11 P3). */
    data class Link(val state: LinkState? = null, val busy: Boolean = false, val lastAdded: Int? = null, val failed: Boolean = false)

    private val _link = MutableStateFlow(Link())
    val link: StateFlow<Link> = _link.asStateFlow()

    val permissions: Set<String> get() = healthConnect.permissions
    val permissionContract get() = healthConnect.permissionContract
    fun installIntent() = healthConnect.installIntent()

    /** Checks the link and, when linked, fetches new sessions. Called when the tab opens and after allowing. */
    fun refreshLink(fetch: Boolean = true) {
        viewModelScope.launch {
            val state = healthConnect.state()
            _link.value = _link.value.copy(state = state)
            if (fetch && state == LinkState.LINKED) fetchNow()
        }
    }

    fun fetchNow() {
        if (_link.value.busy) return
        viewModelScope.launch {
            _link.value = _link.value.copy(busy = true, failed = false)
            val added = runCatching { healthConnect.import(session.currentUser.value.userId) }
            _link.value = _link.value.copy(busy = false, lastAdded = added.getOrNull(), failed = added.isFailure)
        }
    }

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
        if (_askActivity.value) activityAsked()
    }

    fun delete(entry: SportEntry) {
        viewModelScope.launch { sports.delete(entry.id) }
    }

    /** Undo after delete: the same line comes back. */
    fun restore(entry: SportEntry) {
        viewModelScope.launch { sports.save(entry) }
    }

    private companion object {
        const val PREFS = "sport"
        const val KEY_ACTIVITY_ASKED = "activity_asked"
    }
}
