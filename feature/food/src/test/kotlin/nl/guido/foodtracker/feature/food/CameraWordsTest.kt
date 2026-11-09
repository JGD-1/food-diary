package nl.guido.foodtracker.feature.food

import nl.guido.foodtracker.feature.food.nevo.NEVO_ASSET
import nl.guido.foodtracker.feature.food.nevo.NevoParser
import nl.guido.foodtracker.feature.food.search.EverydayWords
import nl.guido.foodtracker.feature.food.search.rankFoods
import nl.guido.foodtracker.feature.food.usda.USDA_ASSET
import nl.guido.foodtracker.feature.food.usda.UsdaParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File

/** Every kind the camera's head start can see (feature/camera, Round 15) finds a food when searched. */
class CameraWordsTest {
    private val nevo = File("src/main/assets/$NEVO_ASSET")
    private val usda = File("src/main/assets/$USDA_ASSET")
    private val foods = nevo.bufferedReader().use { NevoParser.parse(it, dutch = true) } + usda.bufferedReader().use { UsdaParser.parse(it, dutch = true) }
    private val info = nevo.bufferedReader().use { NevoParser.info(it) } + usda.bufferedReader().use { UsdaParser.info(it) }

    private val everyday = File("src/main/assets/everyday_words.csv").bufferedReader().use { EverydayWords.parse(it) }

    /** The search words in produce_head_start.bin (version, kinds, dim, scale, then the words). */
    private fun cameraWords(): List<String> =
        DataInputStream(File("../camera/src/main/assets/produce_head_start.bin").inputStream().buffered()).use { input ->
            input.readInt()
            val kinds = input.readInt()
            input.readInt()
            input.readFloat()
            List(kinds) { input.readUTF() }.filter { it.isNotEmpty() }
        }

    @Test fun everyKindFindsAFood() {
        val missing = cameraWords().filter { rankFoods(it, foods, nevo = info, everyday = everyday.foodsFor(it)).isEmpty() }
        assertTrue("No food found for: $missing", missing.isEmpty())
    }
}
