package nl.guido.foodtracker.feature.camera.read

import nl.guido.foodtracker.core.model.Id
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.sqrt

/**
 * What the picture models saw on one camera frame: [scores] from the starter model (one per
 * kind it knows), and [vector], the picture's fingerprint for comparing with your own examples.
 */
class Picture(val scores: FloatArray, val vector: FloatArray)

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
