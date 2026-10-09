package nl.guido.foodtracker.feature.camera.read

/** What the camera saw on the kitchen scale's display. */
sealed interface ScaleParse {
    data class Grams(val grams: Double) : ScaleParse
    data object Unreadable : ScaleParse
}
