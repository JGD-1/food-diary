package nl.guido.foodtracker.feature.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.CommonPortions
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodNames
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.ProductName
import nl.guido.foodtracker.core.model.WeightSource
import nl.guido.foodtracker.core.model.newId
import nl.guido.foodtracker.feature.camera.read.Barcodes
import nl.guido.foodtracker.feature.camera.read.NutritionLabelParser
import nl.guido.foodtracker.feature.camera.read.PackTextVotes
import nl.guido.foodtracker.feature.camera.read.Picture
import nl.guido.foodtracker.feature.camera.read.Produce
import nl.guido.foodtracker.feature.camera.read.packWords
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.read.Stabilizer
import nl.guido.foodtracker.feature.camera.read.TextLine
import java.util.Optional
import javax.inject.Inject
import kotlin.math.abs

internal enum class CameraMode { BARCODE, LABEL, SCALE }

/** Where the user is on the Scan screen. Every step that can fail offers the alternatives at once. */
internal sealed interface CameraStep {
    data object ScanBarcode : CameraStep
    data class TypeBarcode(val text: String = "", val invalid: Boolean = false) : CameraStep
    data class LookingUp(val code: String) : CameraStep
    /** [known]: the name and brand the food database has without calories; the label scan starts with them. */
    data class NotFound(val code: String, val offline: Boolean, val known: ProductName? = null) : CameraStep
    data class PhotoLabel(val barcode: String?, val known: ProductName? = null) : CameraStep
    data class ReadingLabel(val barcode: String?, val known: ProductName? = null) : CameraStep
    /** [readCount]: how many of the 4 values the label scan found; null when typed by hand. */
    data class EditFood(val draft: FoodDraft, val readCount: Int?) : CameraStep
    /** [pieces]: common pieces for this food ("1 apple ≈ 150 g"), shown as chips next to serving and pack. */
    data class HowMuch(val food: Food, val grams: String = "", val pieces: List<Portion> = emptyList()) : CameraStep
    /**
     * [food] is null when the screen was opened only to weigh something. [search] is not null
     * while naming the food on the scale by hand ([found] are the matches); the reading is kept.
     */
    data class ReadScale(
        val food: Food?,
        val typing: Boolean = false,
        val typed: String = "",
        val search: String? = null,
        val found: List<Food> = emptyList(),
    ) : CameraStep
}

/** What the camera currently sees on the scale. */
internal data class ScaleView(val grams: Double? = null)

/**
 * What the food on the scale may be, while only weighing: guesses from its barcode or the name
 * on the pack, and the one picked ([chosen]), which is logged with the weight.
 */
internal data class Naming(val guesses: List<Food> = emptyList(), val chosen: Food? = null)

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
    /** Common pieces ("1 apple ≈ 150 g"), bound by feature/food; empty until then. */
    private val commonPortions: Optional<CommonPortions>,
    /** Packs learned on this phone, so a pack named once is recognised next time. */
    private val packMemory: PackMemory,
    /** Readable names ("Onion" for a long NEVO name), bound by feature/food. */
    private val foodNames: Optional<FoodNames>,
) : ViewModel() {
    private val guesser = FoodGuesser(foods, foodSource, packMemory)

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

    private val _naming = MutableStateFlow(Naming())
    val naming: StateFlow<Naming> = _naming.asStateFlow()

    /** Only screens that log the food themselves (Today, Log food) can use a named food. */
    private var namingAllowed = false
    private val packText = PackTextVotes()
    private val packCodes = Stabilizer<String>(needed = 2)
    private val codesLookedUp = mutableSetOf<String>()
    private var codeGuess: Food? = null
    private var textGuesses: List<Food> = emptyList()
    private var lookedFor: Any? = null
    /** The latest look at the food itself; its fingerprint is learned when you name the food. */
    private var lastPicture: Picture? = null
    private var guessJob: Job? = null
    private var searchJob: Job? = null

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
        if (next is CameraStep.HowMuch && next.pieces.isEmpty()) loadPieces(next.food)
    }

    private fun loadPieces(food: Food) {
        val source = commonPortions.orElse(null) ?: return
        viewModelScope.launch {
            val pieces = runCatching { source.forFood(food) }.getOrDefault(emptyList())
            if (pieces.isEmpty()) return@launch
            _step.update { if (it is CameraStep.HowMuch && it.food.id == food.id) it.copy(pieces = pieces) else it }
        }
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
                    CameraStep.NotFound(code, offline = false, known = foodSource.nameByBarcode(code))
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
        when (val s = _step.value) {
            is CameraStep.NotFound -> go(CameraStep.PhotoLabel(s.code, s.known))
            // Retaking the photo keeps the name typed so far.
            is CameraStep.EditFood -> go(CameraStep.PhotoLabel(s.draft.barcode, s.draft.knownName()))
            else -> go(CameraStep.PhotoLabel(null))
        }
    }

    fun onLabelPhotoTaken() {
        (_step.value as? CameraStep.PhotoLabel)?.let { go(CameraStep.ReadingLabel(it.barcode, it.known)) }
    }

    /** Text found on the label photo; empty when the photo or reading failed. */
    fun onLabelRead(lines: List<TextLine>) {
        val reading = _step.value as? CameraStep.ReadingLabel ?: return
        val values = NutritionLabelParser.parse(lines)
        val draft = FoodDraft.fromLabel(values, reading.barcode, drinkHint).withName(reading.known)
        go(CameraStep.EditFood(draft, values.foundCount))
    }

    fun typeFood() {
        val notFound = _step.value as? CameraStep.NotFound
        val draft = FoodDraft(isDrink = drinkHint, barcode = notFound?.code).withName(notFound?.known)
        go(CameraStep.EditFood(draft, readCount = null))
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

    /**
     * Accept the grams: the scale reading, or the typed number when typing. A reading below zero
     * (the scale was zeroed with the food on it, then some was taken off) counts what was taken.
     */
    fun useGrams(reading: Double) {
        val grams = abs(reading)
        if (grams <= 0) return
        val s = _step.value as? CameraStep.ReadScale ?: return
        if (s.food == null) {
            val chosen = _naming.value.chosen
            if (chosen != null) rememberPack(chosen)
            _outcome.value = CameraOutcome(chosen?.id, grams)
        } else {
            go(CameraStep.HowMuch(s.food, grams = formatGrams(grams)))
        }
    }

    fun useTypedGrams() {
        val s = _step.value as? CameraStep.ReadScale ?: return
        parseAmount(s.typed)?.let(::useGrams)
    }

    // ---- What is on the scale (only weighing) ----

    fun allowNaming(allowed: Boolean) { namingAllowed = allowed }

    fun title(food: Food): String = foodNames.orElse(null)?.plain(food) ?: food.name

    /** Whether the camera should still look at the pack: only weighing, and nothing picked yet. */
    fun wantsGuess(): Boolean {
        val s = _step.value as? CameraStep.ReadScale ?: return false
        return namingAllowed && s.food == null && _naming.value.chosen == null
    }

    /** A barcode (or none), the text lines and the look at the food on one frame while weighing. */
    fun onPackFrame(code: String?, lines: List<TextLine>, picture: Picture? = null) {
        if (!wantsGuess()) return
        val steadyCode = code?.takeIf { Barcodes.isValid(it) }?.let { packCodes.offer(it) }
        if (steadyCode != null && codesLookedUp.add(steadyCode)) {
            viewModelScope.launch {
                guesser.fromBarcode(steadyCode)?.let { food -> codeGuess = food; showGuesses() }
            }
        }
        packText.offer(lines)
        if (picture != null) lastPicture = picture
        if (guessJob?.isActive == true) return
        val names = packText.names()
        val produce = picture?.kinds.orEmpty()
        // Search again only when something new is in view; a new picture is always worth a look.
        val key = Triple(names, produce, picture)
        if (names.isEmpty() && picture == null) return
        if (key == lookedFor) return
        lookedFor = key
        val words = packText.words()
        guessJob = viewModelScope.launch {
            textGuesses = guesser.guess(names, words, picture)
            showGuesses()
        }
    }

    private fun showGuesses() {
        val all = (listOfNotNull(codeGuess) + textGuesses).distinctBy { it.id }.take(3)
        _naming.update { it.copy(guesses = all) }
    }

    fun choose(food: Food) {
        viewModelScope.launch { if (foods.get(food.id) == null) foods.save(food) }
        _naming.update { it.copy(chosen = food) }
        val s = _step.value as? CameraStep.ReadScale ?: return
        if (s.search != null) go(s.copy(search = null, found = emptyList()))
    }

    fun clearChoice() = _naming.update { it.copy(chosen = null) }

    fun nameItMyself() {
        (_step.value as? CameraStep.ReadScale)?.let { go(it.copy(search = "", found = emptyList(), typing = false)) }
    }

    fun onNameSearch(text: String) {
        val s = _step.value as? CameraStep.ReadScale ?: return
        go(s.copy(search = text))
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_PAUSE_MS)
            val found = guesser.search(text)
            _step.update { if (it is CameraStep.ReadScale && it.search == text) it.copy(found = found) else it }
        }
    }

    /**
     * Teaches the phone: the pack's name words (or, for loose food, what the starter model saw)
     * now point to [food], and the picture is kept as an example, unless the barcode already told.
     */
    private fun rememberPack(food: Food) {
        val words = packText.names().flatMapTo(HashSet()) { packWords(it) }
        val picture = lastPicture
        val produce = picture?.kinds.orEmpty().take(1).map(Produce::token)
        val learn = words.ifEmpty { produce.toSet() }
        if (learn.isNotEmpty()) packMemory.learn(learn, food.id)
        if (picture != null && food.id != codeGuess?.id) packMemory.learnPicture(food.id, picture.vector)
    }

    /** Back button: one step back inside the screen. False means leave the screen. */
    fun back(): Boolean {
        val previous = when (val s = _step.value) {
            is CameraStep.ReadScale -> when {
                s.search != null -> s.copy(search = null, found = emptyList())
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
        private const val SEARCH_PAUSE_MS = 250L
    }
}

internal fun formatGrams(grams: Double): String =
    if (grams == Math.floor(grams)) grams.toLong().toString() else grams.toString()
