package nl.guido.foodtracker.feature.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.WeightSource
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.feature.camera.read.Barcodes
import nl.guido.foodtracker.feature.camera.read.NutritionLabelParser
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.read.Stabilizer
import nl.guido.foodtracker.feature.camera.read.TextLine
import javax.inject.Inject

internal enum class CameraMode { BARCODE, LABEL, SCALE }

/** Where the user is on the Scan screen. Every step that can fail offers the alternatives at once. */
internal sealed interface CameraStep {
    data object ScanBarcode : CameraStep
    data class TypeBarcode(val text: String = "", val invalid: Boolean = false) : CameraStep
    data class LookingUp(val code: String) : CameraStep
    data class NotFound(val code: String, val offline: Boolean) : CameraStep
    data class PhotoLabel(val barcode: String?) : CameraStep
    data class ReadingLabel(val barcode: String?) : CameraStep
    /** [readCount]: how many of the 4 values the label scan found; null when typed by hand. */
    data class EditFood(val draft: FoodDraft, val readCount: Int?) : CameraStep
    /** [pieces]: common pieces for this food ("1 apple ≈ 150 g"), shown as chips next to serving and pack. */
    data class HowMuch(val food: Food, val grams: String = "", val pieces: List<Portion> = emptyList()) : CameraStep
    /** [food] is null when the screen was opened only to weigh something. */
    data class ReadScale(val food: Food?, val typing: Boolean = false, val typed: String = "") : CameraStep
}

/** What the camera currently sees on the scale. */
internal data class ScaleView(val grams: Double? = null)

/** Handed back to the screen that opened the camera. */
internal data class CameraOutcome(val foodId: String?, val grams: Double)

@HiltViewModel
internal class CameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foods: FoodRepository,
    private val foodSource: FoodSource,
    private val session: SessionRepository,
    private val weightSources: Set<@JvmSuppressWildcards WeightSource>,
    private val cameraReadings: CameraScaleReadings,
) : ViewModel() {

    private val mode = when (savedStateHandle.get<String>(ARG_MODE)) {
        "label" -> CameraMode.LABEL
        "scale" -> CameraMode.SCALE
        else -> CameraMode.BARCODE
    }
    private val drinkHint = savedStateHandle.get<Boolean>(ARG_DRINK) ?: false

    private val _step = MutableStateFlow(firstStep())
    val step: StateFlow<CameraStep> = _step.asStateFlow()

    private val _scale = MutableStateFlow(ScaleView())
    val scale: StateFlow<ScaleView> = _scale.asStateFlow()

    private val _outcome = MutableStateFlow<CameraOutcome?>(null)
    val outcome: StateFlow<CameraOutcome?> = _outcome.asStateFlow()

    private val barcodeSteady = Stabilizer<String>(needed = 2)
    private val scaleSteady = Stabilizer<Double>(needed = 3)
    private var scaleJob: Job? = null

    init {
        if (_step.value is CameraStep.ReadScale) listenToScales()
    }

    private fun firstStep(): CameraStep = when (mode) {
        CameraMode.BARCODE -> CameraStep.ScanBarcode
        CameraMode.LABEL -> CameraStep.PhotoLabel(barcode = null)
        CameraMode.SCALE -> CameraStep.ReadScale(food = null)
    }

    private fun go(next: CameraStep) {
        val wasScale = _step.value is CameraStep.ReadScale
        _step.value = next
        if (next is CameraStep.ReadScale && !wasScale) listenToScales()
        if (next !is CameraStep.ReadScale) stopListening()
    }

    // ---- Barcode ----

    /** Called for every barcode the camera sees. */
    fun onBarcodeSeen(code: String) {
        if (_step.value != CameraStep.ScanBarcode || !Barcodes.isValid(code)) return
        barcodeSteady.offer(code)?.let { lookUp(it) }
    }

    fun typeBarcode() = go(CameraStep.TypeBarcode())

    fun onTypedBarcode(text: String) {
        (_step.value as? CameraStep.TypeBarcode)?.let { go(it.copy(text = text.filter { c -> c.isDigit() }, invalid = false)) }
    }

    fun submitTypedBarcode() {
        val current = _step.value as? CameraStep.TypeBarcode ?: return
        if (Barcodes.isValid(current.text)) lookUp(current.text) else go(current.copy(invalid = true))
    }

    private fun lookUp(code: String) {
        go(CameraStep.LookingUp(code))
        viewModelScope.launch {
            val next = try {
                // Our own foods first (labels scanned earlier), then the food database.
                val food = foods.byBarcode(code) ?: foodSource.byBarcode(code)
                if (food == null) {
                    CameraStep.NotFound(code, offline = false)
                } else {
                    // Make sure the food is stored, so the screen that opened us can find it by id.
                    if (foods.get(food.id) == null) foods.save(food)
                    CameraStep.HowMuch(food)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CameraStep.NotFound(code, offline = true)
            }
            go(next)
        }
    }

    fun scanAgain() {
        barcodeSteady.reset()
        go(CameraStep.ScanBarcode)
    }

    // ---- Label ----

    fun scanLabel() {
        val barcode = when (val s = _step.value) {
            is CameraStep.NotFound -> s.code
            is CameraStep.EditFood -> s.draft.barcode
            else -> null
        }
        go(CameraStep.PhotoLabel(barcode))
    }

    fun onLabelPhotoTaken() {
        (_step.value as? CameraStep.PhotoLabel)?.let { go(CameraStep.ReadingLabel(it.barcode)) }
    }

    /** Text found on the label photo; empty when the photo or reading failed. */
    fun onLabelRead(lines: List<TextLine>) {
        val reading = _step.value as? CameraStep.ReadingLabel ?: return
        val values = NutritionLabelParser.parse(lines)
        go(CameraStep.EditFood(FoodDraft.fromLabel(values, reading.barcode, drinkHint), values.foundCount))
    }

    fun typeFood() {
        val barcode = (_step.value as? CameraStep.NotFound)?.code
        go(CameraStep.EditFood(FoodDraft(isDrink = drinkHint, barcode = barcode), readCount = null))
    }

    fun onDraftChange(draft: FoodDraft) {
        (_step.value as? CameraStep.EditFood)?.let { go(it.copy(draft = draft)) }
    }

    fun saveFood() {
        val edit = _step.value as? CameraStep.EditFood ?: return
        val food = edit.draft.toFood(newId(), session.currentUser.value.userId) ?: return
        viewModelScope.launch {
            foods.save(food)
            go(CameraStep.HowMuch(food))
        }
    }

    // ---- How much ----

    fun onGramsChange(text: String) {
        (_step.value as? CameraStep.HowMuch)?.let { go(it.copy(grams = text)) }
    }

    fun readScaleFor(food: Food) = go(CameraStep.ReadScale(food))

    fun finishWithFood() {
        val s = _step.value as? CameraStep.HowMuch ?: return
        val grams = parseAmount(s.grams)?.takeIf { it > 0 } ?: return
        _outcome.value = CameraOutcome(s.food.id, grams)
    }

    // ---- Scale ----

    /** Called for every camera frame while the scale view is open. */
    fun onScaleFrame(parse: ScaleParse) {
        if (parse is ScaleParse.Grams) scaleSteady.offer(parse.grams)?.let { cameraReadings.publish(it) }
    }

    /** Listens to every weight source (camera now, Bluetooth later); the latest reading is shown. */
    private fun listenToScales() {
        scaleSteady.reset()
        _scale.value = ScaleView()
        scaleJob?.cancel()
        scaleJob = viewModelScope.launch {
            weightSources.forEach { source ->
                launch {
                    while (isActive) {
                        val reading = source.readGrams()
                        _scale.update { it.copy(grams = reading.grams) }
                    }
                }
            }
        }
    }

    private fun stopListening() {
        scaleJob?.cancel()
        scaleJob = null
    }

    fun typeGrams() {
        (_step.value as? CameraStep.ReadScale)?.let { go(it.copy(typing = true)) }
    }

    fun onTypedGrams(text: String) {
        (_step.value as? CameraStep.ReadScale)?.let { go(it.copy(typed = text)) }
    }

    /** Accept the grams: the scale reading, or the typed number when typing. */
    fun useGrams(grams: Double) {
        if (grams <= 0) return
        val s = _step.value as? CameraStep.ReadScale ?: return
        if (s.food == null) _outcome.value = CameraOutcome(null, grams)
        else go(CameraStep.HowMuch(s.food, grams = formatGrams(grams)))
    }

    fun useTypedGrams() {
        val s = _step.value as? CameraStep.ReadScale ?: return
        parseAmount(s.typed)?.let(::useGrams)
    }

    /** Back button: one step back inside the screen. False means leave the screen. */
    fun back(): Boolean {
        val previous = when (val s = _step.value) {
            is CameraStep.ReadScale -> when {
                s.typing -> s.copy(typing = false)
                s.food != null -> CameraStep.HowMuch(s.food)
                else -> null
            }
            is CameraStep.TypeBarcode, is CameraStep.NotFound -> CameraStep.ScanBarcode
            else -> null
        } ?: return false
        if (previous == CameraStep.ScanBarcode) barcodeSteady.reset()
        go(previous)
        return true
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_DRINK = "drink"
    }
}

internal fun formatGrams(grams: Double): String =
    if (grams == Math.floor(grams)) grams.toLong().toString() else grams.toString()
