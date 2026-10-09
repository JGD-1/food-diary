package nl.guido.foodtracker.feature.food.nevo

import nl.guido.foodtracker.feature.food.search.FoodRanking

/**
 * The Dutch names of NEVO foods, so "volkoren brood" finds "Bread wholemeal". The food table
 * holds the English name (the app's language); this small index adds the Dutch one for search.
 */
internal class NevoNameIndex(dutchNames: Map<String, String>) {
    /** NEVO code to Dutch name, as in the table. */
    val names: Map<String, String> = dutchNames
    private val folded = dutchNames.mapValues { FoodRanking.fold(it.value) }

    fun dutchName(code: String): String? = names[code]

    /** Codes whose Dutch name holds every typed word. */
    fun matches(query: String, limit: Int = 40): List<String> {
        val words = FoodRanking.words(query)
        if (words.isEmpty()) return emptyList()
        return folded.asSequence()
            .filter { (_, name) -> words.all { it in name } }
            .sortedBy { it.value.length }
            .take(limit)
            .map { it.key }
            .toList()
    }

    /** One line per food: code, tab, Dutch name. */
    fun serialize(): String = names.entries.joinToString("\n") { "${it.key}\t${it.value.replace('\t', ' ')}" }

    companion object {
        val EMPTY = NevoNameIndex(emptyMap())

        fun of(rows: List<NevoRow>) = NevoNameIndex(rows.associate { it.code to it.nameNl })

        fun parse(text: String) = NevoNameIndex(
            text.lineSequence().mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
            }.toMap(),
        )
    }
}
