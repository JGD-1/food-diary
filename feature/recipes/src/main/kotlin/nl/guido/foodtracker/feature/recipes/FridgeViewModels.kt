package nl.guido.foodtracker.feature.recipes

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
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchContainer
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.StoragePlace
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.feature.recipes.logic.StoredBox
import nl.guido.foodtracker.feature.recipes.logic.logEntry
import nl.guido.foodtracker.feature.recipes.logic.mealAt
import nl.guido.foodtracker.feature.recipes.logic.storedBoxes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** "In the fridge" (round 6, D3): every box with something left, shared in the household. */
@HiltViewModel
internal class FridgeViewModel @Inject constructor(
    session: SessionRepository,
    private val recipes: RecipeRepository,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val fridge: List<StoredBox> = emptyList(),
        val freezer: List<StoredBox> = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = session.currentUser.flatMapLatest { user ->
        combine(
            recipes.containers(user.householdId),
            recipes.batches(user.householdId),
            recipes.householdPortions(user.householdId),
        ) { containers, batches, portions ->
            val boxes = storedBoxes(containers, batches, portions)
            State(
                loading = false,
                fridge = boxes.filter { it.container.place == StoragePlace.FRIDGE },
                freezer = boxes.filter { it.container.place == StoragePlace.FREEZER },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** "To freezer" / "Take out", for everyone in the household. */
    fun move(box: BatchContainer, place: StoragePlace) {
        viewModelScope.launch { recipes.saveContainer(box.copy(place = place)) }
    }
}

/**
 * Plates someone dished up for me (round 6, D2), waiting until I add them, and how many boxes
 * are in the fridge (for the fridge button). Shown on the Recipes screen.
 */
@HiltViewModel
internal class WaitingPlatesViewModel @Inject constructor(
    private val session: SessionRepository,
    private val recipes: RecipeRepository,
    private val diary: DiaryRepository,
) : ViewModel() {
    data class Plate(val portion: BatchPortion, val batch: Batch, val servedByName: String?)
    data class State(val plates: List<Plate> = emptyList(), val boxesInFridge: Int = 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = session.currentUser.flatMapLatest { user ->
        combine(
            recipes.waitingPlates(user.userId),
            recipes.batches(user.householdId),
            recipes.householdMembers(user.householdId),
            recipes.containers(user.householdId),
            recipes.householdPortions(user.householdId),
        ) { waiting, batches, members, containers, portions ->
            val batchById = batches.associateBy { it.id }
            val names = members.associate { it.userId to it.displayName.takeIf { name -> name.isNotBlank() } }
            State(
                plates = waiting.mapNotNull { p -> batchById[p.batchId]?.let { Plate(p, it, names[p.servedBy]) } },
                boxesInFridge = storedBoxes(containers, batches, portions).count { it.container.place == StoragePlace.FRIDGE },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** "Add to my diary": today, in the meal that fits the time of day. */
    fun add(plate: Plate) {
        val user = session.currentUser.value
        viewModelScope.launch {
            val entry = plate.batch.logEntry(user.userId, LocalDate.now(), mealAt(LocalTime.now()), plate.portion.grams, Instant.now())
            diary.save(entry)
            recipes.savePortion(plate.portion.copy(logEntryId = entry.id))
        }
    }

    /** "Not mine": the plate's grams go into the fridge as a box, so nothing is lost from the batch. */
    fun notMine(plate: Plate) {
        val user = session.currentUser.value
        viewModelScope.launch {
            val boxes = runCatching { recipes.containers(user.householdId).first() }.getOrDefault(emptyList())
                .filter { it.batchId == plate.batch.id }
            recipes.saveContainer(
                BatchContainer(
                    id = newId(),
                    batchId = plate.batch.id,
                    householdId = plate.batch.householdId,
                    grams = plate.portion.grams,
                    place = StoragePlace.FRIDGE,
                    number = (boxes.maxOfOrNull { it.number } ?: 0) + 1,
                ),
            )
            recipes.deletePortion(plate.portion.id)
        }
    }
}
