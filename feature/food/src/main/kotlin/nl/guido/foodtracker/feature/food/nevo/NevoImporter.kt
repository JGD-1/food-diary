package nl.guido.foodtracker.feature.food.nevo

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.feature.food.data.FoodIds
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies the NEVO table that ships inside the app (assets/nevo/) into the food database,
 * once per NEVO version. A newer NEVO file in a later app update is imported over the old one;
 * ids stay the same, so diary entries keep pointing at their food.
 */
@Singleton
internal class NevoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foods: FoodRepository,
) {
    private val mutex = Mutex()
    private var index: NevoNameIndex? = null
    private var dutchByFoodId: Map<Id, String> = emptyMap()

    private val prefs get() = context.getSharedPreferences("food_nevo", Context.MODE_PRIVATE)
    private val indexFile get() = File(context.filesDir, "nevo-dutch-names.tsv")

    /** Imports when needed and returns the Dutch-name index. Never throws: without NEVO, search still works. */
    suspend fun ready(): NevoNameIndex = mutex.withLock {
        index ?: withContext(Dispatchers.IO) { load() }.also { use(it) }
    }

    /** Dutch name of a NEVO food, if loaded. */
    fun dutchName(foodId: Id): String? = dutchByFoodId[foodId]

    private fun use(loaded: NevoNameIndex) {
        index = loaded
        dutchByFoodId = loaded.names.mapKeys { FoodIds.nevo(it.key) }
    }

    private suspend fun load(): NevoNameIndex = try {
        val asset = context.assets.list(ASSET_DIR)?.sorted()?.lastOrNull { it.endsWith(".csv", ignoreCase = true) }
        when {
            asset == null -> NevoNameIndex.EMPTY
            prefs.getString(KEY_IMPORTED, null) == asset && indexFile.exists() ->
                NevoNameIndex.parse(indexFile.readText())
            else -> importAsset(asset)
        }
    } catch (e: Exception) {
        Log.w(TAG, "NEVO import failed", e)
        NevoNameIndex.EMPTY
    }

    private suspend fun importAsset(asset: String): NevoNameIndex {
        val text = context.assets.open("$ASSET_DIR/$asset").use { it.readBytes() }.let(::decode)
        val rows = NevoParser.parse(text)
        rows.chunked(500).forEach { chunk -> foods.saveAll(chunk.map { it.toFood() }) }
        val index = NevoNameIndex.of(rows)
        indexFile.writeText(index.serialize())
        prefs.edit().putString(KEY_IMPORTED, asset).apply()
        Log.i(TAG, "Imported ${rows.size} NEVO foods from $asset")
        return index
    }

    /** RIVM files are UTF-8; older ones were Windows-1252. Pick whichever reads cleanly. */
    private fun decode(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if ('�' in utf8) bytes.toString(charset("windows-1252")) else utf8
    }

    private companion object {
        const val TAG = "NevoImporter"
        const val ASSET_DIR = "nevo"
        const val KEY_IMPORTED = "imported_asset"
    }
}
