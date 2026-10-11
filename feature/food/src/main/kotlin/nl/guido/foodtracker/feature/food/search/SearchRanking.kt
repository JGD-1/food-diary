package nl.guido.foodtracker.feature.food.search

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.feature.food.nevo.NevoInfo

/** Splits what was typed into words, ignoring case and punctuation. */
internal fun searchWords(text: String): List<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

/**
 * Keeps foods where every typed word starts a word of the name, brand or other-language NEVO name
 * (in any order, so "brown bread" finds "Bread brown"; words of 4+ letters may also sit inside a Dutch
 * compound, so "melk" finds "karnemelk"), and puts the plain, everyday food first:
 * 1. the whole name is what was typed, then names that begin with the typed word ("Apple …", "Kip …"),
 *    or foods in the NEVO group the word names ("bread", "brood": the Bread group);
 * 2. basic foods (fruit, vegetables, dairy, meat, bread…) before drinks and dishes, before cakes,
 *    sweets, sauces and snacks;
 * 3. the "average" version (NEVO "av"/"gem") first; dried, tinned, juice, syrup, powder, gluten-free
 *    and the like after; then shorter names.
 * Own foods come just before basic foods, cached Open Food Facts products count as dishes.
 */
internal fun rankFoods(
    text: String,
    candidates: List<Food>,
    limit: Int = 50,
    nevo: Map<Id, NevoInfo> = emptyMap(),
): List<Food> {
    val words = searchWords(text)
    if (words.isEmpty()) return emptyList()
    val phrase = words.joinToString(" ")
    return candidates
        .mapNotNull { food ->
            val info = nevo[food.id]
            val names = listOfNotNull(food.name, info?.english, info?.dutch).distinct().map(::searchWords)
            val all = names.flatten() + searchWords(food.brand ?: "")
            if (!words.all { word -> all.any { fits(word, it) != null } }) return@mapNotNull null
            val first = words.first()
            val quality = names.mapNotNull { name -> name.withIndex().mapNotNull { (i, w) -> fits(first, w, i) }.minOrNull() }
                .minOrNull() ?: QUALITY_BRAND
            val group = info?.group
            val namesGroup = group != null && words.any { word -> GROUP_WORDS[group].orEmpty().any { same(word, it) } }
            val strong = quality <= 1 || namesGroup
            Ranked(
                food,
                listOf(
                    if (names.any { it.joinToString(" ") == phrase }) 0 else 1,
                    if (strong) 0 else if (quality <= 3) 1 else 2,
                    if (namesGroup) 0 else 1,
                    kind(food, group),
                    if (all.any { it in RARE }) 1 else 0,
                    quality,
                    if (all.any { it in PROCESSED }) 1 else 0,
                    if (all.any { it in AVERAGE }) 0 else 1,
                    food.name.length,
                ),
            )
        }
        .sortedWith(BY_KEYS.thenBy { it.food.name })
        .take(limit)
        .map { it.food }
}

/** True when every typed word fits one of [names]' words; used to pick NEVO foods by their other-language name. */
internal fun matchesWords(words: List<String>, names: List<String>): Boolean {
    val all = names.flatMap(::searchWords)
    return words.all { word -> all.any { fits(word, it) != null } }
}

private class Ranked(val food: Food, val keys: List<Int>)

private val BY_KEYS = Comparator<Ranked> { a, b ->
    a.keys.zip(b.keys).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: 0
}

/**
 * How well a typed word fits a word of a name at [index]: 0 it is the first word (or its plural),
 * 1 another word, 2 the first word starts with it, 3 another word starts with it, 4 inside a word
 * (4+ letters, for Dutch compounds). Null: no fit.
 */
private fun fits(typed: String, word: String, index: Int = 1): Int? = when {
    same(typed, word) -> if (index == 0) 0 else 1
    word.startsWith(typed) -> if (index == 0) 2 else 3
    typed.length >= 4 && typed in word -> 4
    else -> null
}

private const val QUALITY_BRAND = 5

/** Same word, allowing English and Dutch plurals ("apples", "appels", "eieren"). */
private fun same(a: String, b: String): Boolean {
    if (a == b) return true
    val (short, long) = if (a.length <= b.length) a to b else b to a
    return long.startsWith(short) && long.removePrefix(short) in PLURAL_ENDINGS
}

private val PLURAL_ENDINGS = setOf("s", "es", "en", "n", "eren")

private fun kind(food: Food, group: String?): Int = when {
    food.source == FoodOrigin.LABEL || food.source == FoodOrigin.MANUAL -> -1
    food.source == FoodOrigin.OFF -> 1
    group == null -> 0
    group in BASIC_GROUPS -> 0
    group in TREAT_GROUPS -> 2
    else -> 1
}

private val BASIC_GROUPS = setOf(
    "Fruits", "Vegetables", "Potatoes and tubers", "Eggs", "Milk and milk products", "Cheese",
    "Meat and poultry", "Fish, crustacean and shellfish", "Legumes", "Nuts and seeds", "Bread",
    "Cereal products and types of flour", "Fats and oils", "Herbs and spices",
)

private val TREAT_GROUPS = setOf(
    "Pastry and biscuits", "Sugar, sweets and sweet sauces", "Savoury sauces", "Savoury snacks",
    "Foods for special nutritional use", "Miscellaneous foods",
)

/** Words (English and Dutch) that name a NEVO group, so "brood" brings the Bread group first. */
private val GROUP_WORDS = mapOf(
    "Fruits" to setOf("fruit", "vrucht"),
    "Vegetables" to setOf("vegetable", "groente"),
    "Potatoes and tubers" to setOf("potato", "aardappel"),
    "Eggs" to setOf("egg", "ei"),
    "Milk and milk products" to setOf("milk", "melk", "zuivel"),
    "Cheese" to setOf("cheese", "kaas"),
    "Meat and poultry" to setOf("meat", "vlees"),
    "Fish, crustacean and shellfish" to setOf("fish", "vis"),
    "Legumes" to setOf("legume", "peulvrucht", "bean", "boon", "bonen"),
    "Nuts and seeds" to setOf("nut", "noot", "noten", "seed", "zaad", "zaden"),
    "Bread" to setOf("bread", "brood"),
    "Cereal products and types of flour" to setOf("flour", "meel"),
)

/** NEVO's marks for the average of several kinds: the usual pick. */
private val AVERAGE = setOf("av", "average", "gem", "gemiddeld")

/** Kept further down: changed or packed versions of a food. */
private val PROCESSED = setOf(
    "dried", "tinned", "sauce", "juice", "frozen", "syrup", "powder", "fortified", "instant",
    "gedroogd", "blik", "glas", "diepvries", "sap", "stroop", "poeder", "verrijkt",
)

/** Kept further down still: versions few people mean. */
private val RARE = setOf("human", "moeder", "gluten", "glutenvrij", "lactosevrij", "bakery", "bakmix")
