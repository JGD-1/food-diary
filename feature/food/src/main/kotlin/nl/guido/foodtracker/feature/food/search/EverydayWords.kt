package nl.guido.foodtracker.feature.food.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.feature.food.nevoId
import java.io.Reader
import javax.inject.Inject
import javax.inject.Singleton

internal const val EVERYDAY_ASSET = "everyday_words.csv"

/**
 * The bundled list of everyday words for NEVO foods (S2): "havermout" and "oats" find oat flakes,
 * "boterham" finds bread. Only the whole typed text counts, so "kip rauw" is searched as usual.
 */
internal class EverydayWords(private val entries: List<Pair<List<String>, List<Id>>>) {
    /** NEVO food ids for what was typed, the usual pick first; empty when it is not an everyday word. */
    fun foodsFor(text: String): List<Id> {
        val typed = searchWords(text)
        if (typed.isEmpty()) return emptyList()
        return entries.filter { (words, _) -> words.size == typed.size && words.zip(typed).all { (a, b) -> same(a, b) } }
            .flatMap { it.second }
            .distinct()
    }

    companion object {
        val EMPTY = EverydayWords(emptyList())

        /** Lines: words (;)|NEVO codes (;); # starts a comment. */
        fun parse(reader: Reader): EverydayWords = EverydayWords(
            reader.useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .flatMap { line ->
                        val c = line.split('|')
                        require(c.size == 2) { "Everyday words line has ${c.size} columns: $line" }
                        val ids = c[1].split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { nevoId(it) }
                        c[0].split(';').map { searchWords(it) }.filter { it.isNotEmpty() }.map { it to ids }
                    }
                    .toList()
            },
        )
    }
}

/** Reads the bundled everyday words once. */
@Singleton
internal class EverydayWordsAsset @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private var words: EverydayWords? = null

    suspend fun get(): EverydayWords = mutex.withLock {
        words ?: withContext(Dispatchers.IO) {
            context.assets.open(EVERYDAY_ASSET).bufferedReader().use { EverydayWords.parse(it) }
        }.also { words = it }
    }
}
