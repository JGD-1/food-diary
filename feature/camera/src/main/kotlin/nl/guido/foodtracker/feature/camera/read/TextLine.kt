package nl.guido.foodtracker.feature.camera.read

/**
 * One line of text found in a camera picture, with where it sits (pixels, y grows downwards).
 * Plain Kotlin so the readers can be tested without a phone.
 */
data class TextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val height: Float get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Accepts a value only after it was seen [needed] times in a row, so a single misread
 * camera frame never counts. Frames with nothing readable are skipped, not counted.
 */
class Stabilizer<T : Any>(private val needed: Int) {
    private var last: T? = null
    private var count = 0

    /** Feed one frame's value; returns the value once it is steady, else null. */
    fun offer(value: T?): T? {
        if (value == null) return null
        if (value == last) count++ else { last = value; count = 1 }
        return if (count >= needed) value else null
    }

    fun reset() { last = null; count = 0 }
}
