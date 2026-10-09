package nl.guido.foodtracker.feature.food.nevo

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.feature.food.data.FoodIds

/** One NEVO food, numbers exactly as in the table (the licence forbids changing them). */
internal data class NevoRow(
    val code: String,
    val nameNl: String,
    val nameEn: String,
    val group: String,
    val per100g: Nutrients,
    val isDrink: Boolean,
) {
    fun toFood() = Food(
        id = FoodIds.nevo(code),
        name = nameEn.ifBlank { nameNl },
        per100g = per100g,
        source = FoodOrigin.NEVO,
        isDrink = isDrink,
    )
}

/**
 * Reads the NEVO table as RIVM ships it (NEVO2025_v9.0.csv: "|" separated, quoted, Dutch decimal commas;
 * ";" and tab work too). Columns are found by their header names, so a column moving doesn't break the import.
 */
internal object NevoParser {
    class FormatException(message: String) : Exception(message)

    fun parse(text: String): List<NevoRow> {
        val lines = text.removePrefix("﻿").lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) throw FormatException("empty file")
        val headerLine = lines.next()
        val sep = listOf('|', '\t', ';', ',').maxBy { c -> headerLine.count { it == c } }
        val header = split(headerLine, sep).map { it.trim().lowercase() }

        fun column(what: String, test: (String) -> Boolean): Int =
            header.indexOfFirst(test).takeIf { it >= 0 } ?: throw FormatException("no $what column")
        fun optional(test: (String) -> Boolean): Int = header.indexOfFirst(test)
        fun head(h: String) = h.substringBefore(' ').substringBefore('(').trim()

        val codeCol = column("NEVO-code") { it.replace('_', '-').replace(' ', '-').startsWith("nevo-code") }
        val nlCol = column("Dutch name") { "voedingsmiddelnaam" in it || "dutch food name" in it }
        val enCol = optional { "engelse naam" in it || it == "food name" || it.endsWith("/food name") }
        val groupEnCol = optional { it == "food group" || it.endsWith("/food group") }
        val groupNlCol = optional { "voedingsmiddelgroep" in it }
        val quantityCol = optional { "hoeveelheid" in it || it == "quantity" }
        val kcalCol = column("ENERCC (kcal)") { head(it) == "enercc" }
        val kjCol = optional { head(it) == "enercj" }
        val protCol = column("PROT (g)") { head(it) == "prot" }
        val choCol = column("CHO (g)") { head(it) == "cho" }
        val fatCol = column("FAT (g)") { head(it) == "fat" }

        val rows = mutableListOf<NevoRow>()
        for (line in lines) {
            val cells = split(line, sep)
            fun cell(i: Int) = if (i in cells.indices) cells[i].trim() else ""
            val code = cell(codeCol)
            if (code.isEmpty()) continue
            // Our foods are per 100 g. NEVO's few "per 100ml" rows are infant formulas; we leave them out
            // rather than change their numbers.
            if ("ml" in cell(quantityCol).lowercase()) continue
            val kcal = number(cell(kcalCol)) ?: number(cell(kjCol))?.let { it / 4.184 } ?: continue
            val groupEn = cell(groupEnCol)
            val groupNl = cell(groupNlCol)
            rows += NevoRow(
                code = code,
                nameNl = cell(nlCol),
                nameEn = cell(enCol),
                group = groupEn.ifEmpty { groupNl },
                per100g = Nutrients(
                    kcal = kcal,
                    protein = number(cell(protCol)) ?: 0.0,
                    carbs = number(cell(choCol)) ?: 0.0,
                    fat = number(cell(fatCol)) ?: 0.0,
                ),
                isDrink = isDrinkGroup(groupEn) || isDrinkGroup(groupNl),
            )
        }
        if (rows.isEmpty()) throw FormatException("no foods found")
        return rows
    }

    private fun isDrinkGroup(group: String): Boolean {
        val g = group.lowercase()
        return "beverage" in g || "drank" in g || "dranken" in g
    }

    /** "12,5" and "12.5" are both 12.5; empty or "-" means unknown. */
    private fun number(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    /** Splits one line, honouring "quoted; values". */
    private fun split(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == sep && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
