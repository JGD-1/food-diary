package nl.guido.foodtracker.feature.food.names

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodNames
import nl.guido.foodtracker.core.model.FoodOrigin
import java.io.Reader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal const val SHORT_FORMS_ASSET = "nevo_short_forms.csv"

/** The plain words for NEVO's short forms in one language: "w" → "with", "sat fa" → "saturated fatty acids". */
internal class ShortForms(forms: Map<String, String>) {
    /** Keyed by the short form's words; longer forms are tried first. */
    private val byWords = forms.mapKeys { it.key.split(' ').filter(String::isNotEmpty) }
    private val longest = byWords.keys.maxOfOrNull { it.size } ?: 1

    /**
     * The app's readable full name for a NEVO name (S1): the short forms written out, the added part set
     * off with a comma, e.g. "Apple wo skin av" → "Apple, without skin, average". It is shown as the main
     * name; the NEVO name itself is not changed and stays visible under it. Null when the name has no short forms.
     */
    fun readableName(name: String): String? {
        val words = name.split(' ').filter(String::isNotEmpty)
        val out = mutableListOf<String>()
        var firstChange = -1
        var i = 0
        while (i < words.size) {
            val match = (minOf(longest, words.size - i) downTo 1).firstNotNullOfOrNull { n ->
                val part = words.subList(i, i + n)
                // A comma after the last word ("vit,") stays after the plain words.
                val comma = part.last().endsWith(',')
                val key = if (comma) part.dropLast(1) + part.last().dropLast(1) else part
                byWords[key]?.let { Triple(n, it, comma) }
            }
            if (match == null) {
                out += words[i]
                i++
            } else {
                val (n, plain, comma) = match
                if (firstChange < 0) firstChange = out.size
                // "average" reads as an afterthought: "without skin, average".
                if (plain in AVERAGE && out.size > firstChange && !out.last().endsWith(',')) out[out.lastIndex] += ","
                out += if (comma) "$plain," else plain
                i += n
            }
        }
        if (firstChange < 0) return null
        val head = out.take(firstChange).joinToString(" ").trimEnd(',')
        val tail = out.drop(firstChange).joinToString(" ").trimEnd(',')
        return if (head.isEmpty()) tail.replaceFirstChar { it.uppercase() } else "$head, $tail"
    }

    private companion object {
        val AVERAGE = setOf("average", "gemiddeld")
    }
}

/** Reads the bundled list. Lines: language|short form|plain words; # starts a comment. */
internal object ShortFormsParser {
    fun parse(reader: Reader): Map<String, ShortForms> = reader.useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val c = line.split('|')
                require(c.size == 3) { "Short form line has ${c.size} columns: $line" }
                Triple(c[0].trim(), c[1].trim(), c[2].trim())
            }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, pairs) -> ShortForms(pairs.toMap()) }
    }
}

/**
 * The app's readable full name for NEVO foods (S1), in the phone's language, from the bundled list of NEVO
 * short forms. Screens show it as the main name with the unchanged NEVO name as a small line under it.
 * Only for NEVO foods; the readable name is the app's addition (named so on Profile → Data sources).
 */
@Singleton
internal class BundledFoodNames(
    private val load: () -> Map<String, ShortForms>,
    private val language: () -> String = { Locale.getDefault().language },
) : FoodNames {
    @Inject constructor(@ApplicationContext context: Context) : this({
        context.assets.open(SHORT_FORMS_ASSET).bufferedReader().use { ShortFormsParser.parse(it) }
    })

    // A small file, read once on first use.
    private val forms by lazy { runCatching(load).getOrDefault(emptyMap()) }
    private val lines = ConcurrentHashMap<String, Any>()

    override fun plain(food: Food): String? {
        if (food.source != FoodOrigin.NEVO) return null
        val lang = if (language() == "nl") "nl" else "en"
        val table = forms[lang] ?: return null
        val line = lines.getOrPut("$lang:${food.name}") { table.readableName(food.name) ?: NONE }
        return line as? String
    }

    private companion object {
        val NONE = Any()
    }
}
