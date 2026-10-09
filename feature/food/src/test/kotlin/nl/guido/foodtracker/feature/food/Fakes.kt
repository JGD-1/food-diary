package nl.guido.foodtracker.feature.food

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.food.net.WebClient
import nl.guido.foodtracker.feature.food.net.WebResponse
import java.io.IOException

internal class FakeFoods(vararg start: Food) : FoodRepository {
    val rows = start.associateBy { it.id }.toMutableMap()
    override suspend fun get(id: Id) = rows[id]
    override suspend fun byBarcode(barcode: String) = rows.values.firstOrNull { it.barcode == barcode }

    // Same as the Room query: one piece of text, anywhere in name or brand.
    override fun search(text: String, limit: Int): Flow<List<Food>> = flowOf(
        rows.values.filter {
            it.name.contains(text, ignoreCase = true) || it.brand?.contains(text, ignoreCase = true) == true
        }.sortedBy { it.name }.take(limit),
    )

    override suspend fun save(food: Food) { rows[food.id] = food }
    override suspend fun saveAll(foods: List<Food>) = foods.forEach { rows[it.id] = it }
}

internal class FakeWeb(var reply: (String) -> WebResponse = { WebResponse(404, "") }) : WebClient {
    val urls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()
    val bodies = mutableListOf<String>()
    var offline = false

    override suspend fun get(url: String, headers: Map<String, String>): WebResponse {
        if (offline) throw IOException("offline")
        urls += url
        this.headers += headers
        return reply(url)
    }

    override suspend fun postJson(url: String, headers: Map<String, String>, json: String): WebResponse {
        bodies += json
        return get(url, headers)
    }
}

internal fun food(name: String, source: FoodOrigin = FoodOrigin.NEVO, brand: String? = null, barcode: String? = null) =
    Food(id = "id-$name-$source", name = name, brand = brand, barcode = barcode, per100g = Nutrients(100.0, 1.0, 1.0, 1.0), source = source)
