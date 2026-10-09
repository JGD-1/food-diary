package nl.guido.foodtracker.feature.camera

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.guido.foodtracker.core.data.repo.CurrentUser
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.read.TextLine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {
    private val cola = Food(
        "off-cola", "Cola", barcode = "5449000000996",
        per100g = Nutrients(42.0, 0.0, 10.6, 0.0), source = FoodOrigin.OFF, isDrink = true,
    )

    private class FakeFoods : FoodRepository {
        val saved = mutableMapOf<Id, Food>()
        override suspend fun get(id: Id) = saved[id]
        override suspend fun byBarcode(barcode: String) = saved.values.firstOrNull { it.barcode == barcode }
        override fun search(text: String, limit: Int): Flow<List<Food>> = flowOf(emptyList())
        override suspend fun save(food: Food) { saved[food.id] = food }
        override suspend fun saveAll(foods: List<Food>) = foods.forEach { saved[it.id] = it }
    }

    private class FakeSource(val food: Food?, val fail: Boolean = false) : FoodSource {
        override suspend fun byBarcode(code: String): Food? {
            if (fail) throw IOException("no internet")
            return food?.takeIf { it.barcode == code }
        }
        override suspend fun search(text: String) = emptyList<Food>()
    }

    private object Session : SessionRepository {
        override val currentUser: StateFlow<CurrentUser> = MutableStateFlow(CurrentUser("user-1", "home-1", "Me"))
    }

    private val foods = FakeFoods()
    private val readings = CameraScaleReadings()

    private fun vm(source: FoodSource = FakeSource(cola), args: Map<String, Any?> = emptyMap()) =
        CameraViewModel(SavedStateHandle(args), foods, source, Session, setOf(CameraScaleWeightSource(readings)), readings)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun CameraViewModel.see(code: String) = repeat(2) { onBarcodeSeen(code) }

    @Test fun `barcode found asks how much, then hands back food and grams`() {
        val vm = vm()
        vm.see("5449000000996")
        assertEquals(CameraStep.HowMuch(cola), vm.step.value)
        assertEquals(cola, foods.saved["off-cola"]) // stored so the caller can find it
        vm.onGramsChange("330")
        vm.finishWithFood()
        assertEquals(CameraOutcome("off-cola", 330.0), vm.outcome.value)
    }

    @Test fun `one glimpse of a barcode is not enough`() {
        val vm = vm()
        vm.onBarcodeSeen("5449000000996")
        assertEquals(CameraStep.ScanBarcode, vm.step.value)
    }

    @Test fun `unknown product offers the label scan with the barcode kept`() {
        val vm = vm(FakeSource(null))
        vm.see("8710398527554")
        assertEquals(CameraStep.NotFound("8710398527554", offline = false), vm.step.value)
        vm.scanLabel()
        assertEquals(CameraStep.PhotoLabel("8710398527554"), vm.step.value)
    }

    @Test fun `no internet offers the alternatives too`() {
        val vm = vm(FakeSource(null, fail = true))
        vm.see("8710398527554")
        assertEquals(CameraStep.NotFound("8710398527554", offline = true), vm.step.value)
    }

    @Test fun `label scan creates a food owned by the user`() {
        val vm = vm(FakeSource(null))
        vm.see("8710398527554")
        vm.scanLabel()
        vm.onLabelPhotoTaken()
        vm.onLabelRead(
            listOf("Voedingswaarde per 100 g", "Energie 1600 kJ/382 kcal", "Vet 3,1 g", "Koolhydraten 75 g", "Eiwitten 11 g")
                .mapIndexed { i, t -> TextLine(t, 0f, i * 40f, 300f, i * 40f + 30f) },
        )
        val edit = vm.step.value as CameraStep.EditFood
        assertEquals(4, edit.readCount)
        vm.onDraftChange(edit.draft.copy(name = "Crackers"))
        vm.saveFood()
        val food = (vm.step.value as CameraStep.HowMuch).food
        assertEquals(FoodOrigin.LABEL, food.source)
        assertEquals("user-1", food.ownerId)
        assertEquals("8710398527554", food.barcode)
        assertEquals(Nutrients(382.0, 11.0, 75.0, 3.1), food.per100g)
        assertEquals(food, foods.saved[food.id])
    }

    @Test fun `unreadable label opens the form to type`() {
        val vm = vm(args = mapOf(CameraViewModel.ARG_MODE to "label"))
        vm.onLabelPhotoTaken()
        vm.onLabelRead(emptyList())
        assertEquals(0, (vm.step.value as CameraStep.EditFood).readCount)
    }

    @Test fun `opened from Drinks, a typed food defaults to a drink`() {
        val vm = vm(args = mapOf(CameraViewModel.ARG_MODE to "label", CameraViewModel.ARG_DRINK to true))
        vm.typeFood()
        assertTrue((vm.step.value as CameraStep.EditFood).draft.isDrink)
    }

    @Test fun `weigh only, a steady scale reading is handed back as grams`() {
        val vm = vm(args = mapOf(CameraViewModel.ARG_MODE to "scale"))
        vm.onScaleFrame(ScaleParse.Grams(245.0))
        vm.onScaleFrame(ScaleParse.Grams(245.0))
        assertNull(vm.scale.value.grams)
        vm.onScaleFrame(ScaleParse.Grams(245.0))
        assertEquals(245.0, vm.scale.value.grams)
        vm.useGrams(245.0)
        assertEquals(CameraOutcome(null, 245.0), vm.outcome.value)
    }

    @Test fun `typed grams are the fallback`() {
        val vm = vm(args = mapOf(CameraViewModel.ARG_MODE to "scale"))
        vm.typeGrams()
        vm.onTypedGrams("12,5")
        vm.useTypedGrams()
        assertEquals(CameraOutcome(null, 12.5), vm.outcome.value)
    }

    @Test fun `scale reading fills in how much`() {
        val vm = vm()
        vm.see("5449000000996")
        vm.readScaleFor(cola)
        repeat(3) { vm.onScaleFrame(ScaleParse.Grams(330.0)) }
        vm.useGrams(330.0)
        assertEquals(CameraStep.HowMuch(cola, grams = "330"), vm.step.value)
    }

    @Test fun `back goes one step back inside the screen`() {
        val vm = vm()
        vm.see("5449000000996")
        vm.readScaleFor(cola)
        assertTrue(vm.back())
        assertEquals(CameraStep.HowMuch(cola), vm.step.value)
    }
}
