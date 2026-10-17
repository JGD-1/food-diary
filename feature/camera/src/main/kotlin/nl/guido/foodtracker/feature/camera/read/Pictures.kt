package nl.guido.foodtracker.feature.camera.read

import nl.guido.foodtracker.core.model.Id
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.sqrt

/**
 * What the picture models saw on one camera frame: [scores] from the starter model (one per
 * kind it knows), [vector], the picture's fingerprint for comparing with your own examples, and
 * [kinds], the fruit or vegetables it probably is as search words, surest first.
 */
class Picture(
    val scores: FloatArray,
    val vector: FloatArray,
    val kinds: List<String> = Produce.likely(scores),
)

/**
 * The head start: a small layer trained on about 2,300 openly licensed photos (Open Images,
 * CC BY 2.0) of 38 fruits and vegetables sold in Dutch supermarkets, plus pictures with no food.
 * It reads a picture's fingerprint and says which kinds it probably is. Made by
 * tools/produce-head-start (see MODELS.md); only the numbers are in the app, no photos.
 */
class HeadStart(
    /** Search word per kind; an empty word is "no food". */
    val words: List<String>,
    /** [dim] × [words].size weights, row by row. */
    private val weights: FloatArray,
    private val bias: FloatArray,
    private val scale: Float,
) {
    private val dim = weights.size / words.size

    /** Likely kinds as search words, surest first; empty when it sees no food or isn't sure. */
    fun kinds(vector: FloatArray, atLeast: Float = MIN_SURE, limit: Int = 3): List<String> {
        if (vector.size != dim) return emptyList()
        val length = sqrt(vector.fold(0f) { sum, x -> sum + x * x }).takeIf { it > 0f } ?: return emptyList()
        val z = FloatArray(words.size) { k ->
            var sum = bias[k]
            for (i in 0 until dim) sum += vector[i] / length * scale * weights[i * words.size + k]
            sum
        }
        val top = z.max()
        val e = FloatArray(z.size) { kotlin.math.exp(z[it] - top) }
        val total = e.sum()
        val p = FloatArray(z.size) { e[it] / total }
        val noFood = words.indexOf("")
        if (noFood >= 0 && p[noFood] >= 0.5f) return emptyList()
        val ranked = words.indices.filter { words[it].isNotEmpty() }.sortedByDescending { p[it] }
        if (ranked.isEmpty() || p[ranked[0]] < atLeast) return emptyList()
        return ranked.take(limit).filter { p[it] >= MIN_ALSO }.map { words[it] }.distinct()
    }

    companion object {
        /** How sure the first guess must be; set from the test on held-back photos. */
        const val MIN_SURE = 0.35f
        /** The second and third guesses are shown from this. */
        const val MIN_ALSO = 0.05f
        private const val VERSION = 1

        fun decode(bytes: ByteArray): HeadStart? = try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != VERSION) return null
                val kinds = input.readInt()
                val dim = input.readInt()
                val scale = input.readFloat()
                val words = List(kinds) { input.readUTF() }
                val weights = FloatArray(dim * kinds) { input.readFloat() }
                val bias = FloatArray(kinds) { input.readFloat() }
                HeadStart(words, weights, bias, scale)
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Loose fruit and vegetables the starter model knows (EfficientNet-Lite0, trained on ImageNet),
 * with the word to search for. It doesn't know onion, carrot, tomato or potato: those are taught.
 */
object Produce {
    private val SEARCH = mapOf(
        936 to "cabbage", 937 to "broccoli", 938 to "cauliflower", 939 to "courgette",
        940 to "pumpkin", 941 to "pumpkin", 942 to "pumpkin", 943 to "cucumber", 944 to "artichoke",
        945 to "sweet pepper", 947 to "mushroom", 948 to "apple", 949 to "strawberry", 950 to "orange",
        951 to "lemon", 952 to "fig", 953 to "pineapple", 954 to "banana", 957 to "pomegranate", 987 to "sweetcorn",
    )

    /** Search words for the produce the model is fairly sure of, surest first. */
    fun likely(scores: FloatArray, atLeast: Float = 0.25f, limit: Int = 2): List<String> =
        SEARCH.entries.filter { it.key < scores.size && scores[it.key] >= atLeast }
            .sortedByDescending { scores[it.key] }
            .map { it.value }.distinct().take(limit)

    /** The produce as a "word", so a pick for it is learned like the words on a pack. */
    fun token(word: String) = "#" + word.replace(" ", "")
}

/**
 * Pictures you named, as fingerprints: a new picture close to one of them is probably the same food.
 * Kept small: at most [PER_FOOD] per food (the newest) and [MAX] in all.
 */
class PictureMemory(val examples: List<Example> = emptyList()) {
    class Example(val foodId: Id, val vector: FloatArray)

    /** Foods with an example at least [atLeast] alike (1 = the same picture), most alike first. */
    fun nearest(vector: FloatArray, atLeast: Float = MIN_ALIKE, limit: Int = 2): List<Id> {
        val v = normalized(vector)
        return examples.groupBy { it.foodId }
            .mapValues { (_, list) -> list.maxOf { dot(it.vector, v) } }
            .filterValues { it >= atLeast }
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key }
    }

    fun learn(foodId: Id, vector: FloatArray): PictureMemory {
        val mine = examples.filter { it.foodId == foodId }.takeLast(PER_FOOD - 1)
        val others = examples.filter { it.foodId != foodId }
        val kept = (others + mine).takeLast(MAX - 1)
        return PictureMemory(kept + Example(foodId, normalized(vector)))
    }

    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            out.writeInt(examples.size)
            examples.forEach { e ->
                out.writeUTF(e.foodId)
                out.writeInt(e.vector.size)
                e.vector.forEach(out::writeFloat)
            }
        }
        return bytes.toByteArray()
    }

    companion object {
        const val PER_FOOD = 10
        const val MAX = 400
        /** How alike counts as the same food; to be tuned on Guido's photos. */
        const val MIN_ALIKE = 0.75f
        private const val VERSION = 1

        fun decode(bytes: ByteArray?): PictureMemory {
            if (bytes == null) return PictureMemory()
            return try {
                DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    if (input.readInt() != VERSION) return PictureMemory()
                    PictureMemory(
                        List(input.readInt()) {
                            val id = input.readUTF()
                            Example(id, FloatArray(input.readInt()) { input.readFloat() })
                        },
                    )
                }
            } catch (e: Exception) { // a broken or old file starts empty
                PictureMemory()
            }
        }

        private fun normalized(v: FloatArray): FloatArray {
            val length = sqrt(v.fold(0f) { sum, x -> sum + x * x })
            return if (length == 0f) v.copyOf() else FloatArray(v.size) { v[it] / length }
        }

        private fun dot(a: FloatArray, b: FloatArray): Float =
            if (a.size != b.size) 0f else a.indices.fold(0f) { sum, i -> sum + a[i] * b[i] }
    }
}
