package nl.guido.foodtracker.feature.camera

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodSource
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.feature.camera.read.PackWords
import nl.guido.foodtracker.feature.camera.read.packSearches
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The packs learned on this phone (their words → the food you picked). */
internal interface PackMemory {
    suspend fun get(): PackWords
    fun learn(words: Set<String>, foodId: Id)
}

/**
 * Guesses what is on the scale from what the camera reads on it: the barcode first (exact),
 * then packs learned earlier, then a search for the name printed on the pack.
 */
internal class FoodGuesser(
    private val foods: FoodRepository,
    private val foodSource: FoodSource,
    private val memory: PackMemory,
) {
    /** The food for a barcode in view; stored so the screen that opened the camera can find it. */
    suspend fun fromBarcode(code: String): Food? {
        val food = try {
            foods.byBarcode(code) ?: foodSource.byBarcode(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // offline: the text on the pack may still tell
        } ?: return null
        if (foods.get(food.id) == null) foods.save(food)
        return food
    }

    /** Up to [limit] guesses from the pack's name lines and words, best first. */
    suspend fun fromText(names: List<String>, words: Set<String>, limit: Int = 3): List<Food> {
        val learned = memory.get().lookup(words).mapNotNull { foods.get(it) }
        val searched = packSearches(names).mapNotNull { query ->
            runCatching { foodSource.search(query).firstOrNull() }.getOrNull()
        }
        return (learned + searched).distinctBy { it.id }.take(limit)
    }

    /** Typed search on the "Name it" panel: my foods and NEVO, on the phone. */
    suspend fun search(text: String): List<Food> =
        if (text.isBlank()) emptyList() else runCatching { foodSource.search(text) }.getOrDefault(emptyList()).take(30)
}

/** [PackMemory] in a small file on the phone (filesDir/pack-words.json), not shared or uploaded. */
@Singleton
internal class PackWordsStore @Inject constructor(@ApplicationContext context: Context) : PackMemory {
    private val file = File(context.filesDir, "pack-words.json")
    private val lock = Mutex()
    private var words: PackWords? = null
    /** Its own scope, so learning finishes even when the camera closes right away. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun get(): PackWords = lock.withLock { loaded() }

    override fun learn(words: Set<String>, foodId: Id) {
        scope.launch {
            lock.withLock {
                val before = loaded()
                val after = before.learn(words, foodId)
                if (after == before) return@withLock
                this@PackWordsStore.words = after
                try {
                    file.writeText(after.encode())
                } catch (e: IOException) {
                    // Still known while the app runs.
                }
            }
        }
    }

    private suspend fun loaded(): PackWords =
        words ?: withContext(Dispatchers.IO) { PackWords.decode(runCatching { file.readText() }.getOrNull()) }.also { words = it }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PackMemoryModule {
    @Binds abstract fun packMemory(impl: PackWordsStore): PackMemory
}
