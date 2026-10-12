package nl.guido.foodtracker.feature.recipes.note

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.feature.recipes.logic.NoteWords
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The note words learned on this phone ("kbonen" → kidney beans), kept in a small file. */
@Singleton
internal class NoteWordsStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "note-words.json")
    private val lock = Mutex()
    private var words: NoteWords? = null
    /** Its own scope, so learning finishes even when the scanner closes right away. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun get(): NoteWords = lock.withLock { loaded() }

    /** Remembers each (food words, food) pair. */
    fun learn(pairs: List<Pair<String, Id>>) {
        if (pairs.isNotEmpty()) scope.launch { save(pairs) }
    }

    private suspend fun save(pairs: List<Pair<String, Id>>) = lock.withLock {
        val before = loaded()
        val after = pairs.fold(before) { w, (food, id) -> w.learn(food, id) }
        if (after == before) return@withLock
        words = after
        try {
            file.writeText(after.encode())
        } catch (e: IOException) {
            // Still known while the app runs.
        }
    }

    private suspend fun loaded(): NoteWords =
        words ?: withContext(Dispatchers.IO) { NoteWords.decode(runCatching { file.readText() }.getOrNull()) }.also { words = it }
}
