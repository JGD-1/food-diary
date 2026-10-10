package nl.guido.foodtracker.feature.camera

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.ui.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodEditTest {
    private val muesli = Food(
        "off-muesli", "Muesli", brand = "Brand", barcode = "87654321",
        per100g = Nutrients(370.0, 9.0, 60.0, 6.0, fibre = 8.5), source = FoodOrigin.OFF,
        servingG = 45.0, packageG = 750.0,
    )
    private val apple = Food("nevo-apple", "Apple w skin av", per100g = Nutrients(54.0, 0.3, 12.0, 0.1), source = FoodOrigin.NEVO)

    private class FakeFoods(vararg start: Food) : FoodRepository {
        val saved = start.associateBy { it.id }.toMutableMap()
        override suspend fun get(id: Id) = saved[id]
        override suspend fun byBarcode(barcode: String) = saved.values.firstOrNull { it.barcode == barcode }
        override fun search(text: String, limit: Int): Flow<List<Food>> = flowOf(emptyList())
        override suspend fun save(food: Food) { saved[food.id] = food }
        override suspend fun saveAll(foods: List<Food>) = foods.forEach { saved[it.id] = it }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(foods: FoodRepository, id: String) =
        FoodEditViewModel(SavedStateHandle(mapOf(Routes.ARG_FOOD_ID to id)), foods)

    @Test fun `edits keep the id, source, barcode and pack sizes`() {
        val foods = FakeFoods(muesli)
        val vm = vm(foods, muesli.id)
        val editing = vm.state.value as FoodEditState.Editing
        assertEquals("370", editing.draft.kcal)
        assertEquals("8.5", editing.draft.fibre)
        assertEquals("", editing.draft.salt)
        vm.onDraftChange(editing.draft.copy(name = "Crunchy muesli", kcal = "410", salt = "0,3"))
        vm.save()
        assertEquals(FoodEditState.Saved, vm.state.value)
        val saved = foods.saved.getValue(muesli.id)
        assertEquals("Crunchy muesli", saved.name)
        assertEquals(410.0, saved.per100g.kcal, 0.0)
        assertEquals(0.3, saved.per100g.salt!!, 1e-9)
        assertEquals(8.5, saved.per100g.fibre!!, 1e-9)
        assertEquals(FoodOrigin.OFF, saved.source)
        assertEquals("87654321", saved.barcode)
        assertEquals(750.0, saved.packageG!!, 0.0)
    }

    @Test fun `NEVO foods and unknown ids can't be edited`() {
        assertEquals(FoodEditState.CannotEdit, vm(FakeFoods(apple), apple.id).state.value)
        assertEquals(FoodEditState.CannotEdit, vm(FakeFoods(), "gone").state.value)
    }

    @Test fun `amount chips come from serving, pack and common pieces`() {
        val chips = amountChips(muesli, listOf(Portion(30.0, "1 bowl")))
        assertEquals(
            listOf(
                AmountChip(AmountChip.Kind.SERVING, 45.0),
                AmountChip(AmountChip.Kind.PACK, 750.0),
                AmountChip(AmountChip.Kind.PIECE, 30.0, "1 bowl"),
            ),
            chips,
        )
        assertTrue(amountChips(apple).isEmpty())
        assertEquals(1, amountChips(muesli.copy(packageG = 45.0)).size)
    }
}
