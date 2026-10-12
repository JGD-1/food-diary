package nl.guido.foodtracker.feature.recipes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.feature.recipes.logic.BatchDraft
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one batch being cooked on this phone (B1), kept in a small file so it survives the app
 * being closed. Never synced: only the finished batch is shared with the household.
 */
@Singleton
internal class BatchDraftStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "batch-draft.json")
    private val lock = Mutex()
    private val _draft = MutableStateFlow<BatchDraft?>(null)
    private var loaded = false

    /** The draft, or null when nothing is being cooked. Call [load] once to fill it. */
    val draft: StateFlow<BatchDraft?> = _draft.asStateFlow()

    suspend fun load(): BatchDraft? = lock.withLock {
        if (!loaded) {
            _draft.value = withContext(Dispatchers.IO) { BatchDraft.decode(runCatching { file.readText() }.getOrNull()) }
            loaded = true
        }
        _draft.value
    }

    suspend fun save(draft: BatchDraft) = lock.withLock {
        _draft.value = draft
        loaded = true
        withContext(Dispatchers.IO) {
            try {
                file.writeText(draft.encode())
            } catch (e: IOException) {
                // Still kept in memory while the app runs.
            }
        }
    }

    suspend fun clear() = lock.withLock {
        _draft.value = null
        loaded = true
        withContext(Dispatchers.IO) { file.delete() }
    }
}
