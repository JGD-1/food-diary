package nl.guido.foodtracker.feature.camera.read

/**
 * A packet held upside down reads as nonsense. When the photo read the right way up shows fewer
 * than two of calories, fat, carbs and protein, the app reads it again turned around and keeps
 * whichever read finds more.
 */
fun needsTurning(upright: List<TextLine>): Boolean = NutritionLabelParser.parse(upright).foundCount < 2

fun pickLabelText(upright: List<TextLine>, turned: List<TextLine>): List<TextLine> =
    if (NutritionLabelParser.parse(turned).foundCount > NutritionLabelParser.parse(upright).foundCount) turned else upright

/** [needsTurning] and [pickLabelText] together, for a reader that can read turned on the spot. */
fun readLabelText(upright: List<TextLine>, readTurned: () -> List<TextLine>): List<TextLine> =
    if (needsTurning(upright)) pickLabelText(upright, readTurned()) else upright

/**
 * Lines of an upside-down packet in reading order. The text reader reads each line the right way round,
 * but lists them bottom to top with upside-down positions; turning the positions around fixes both.
 */
fun turnAround(lines: List<TextLine>): List<TextLine> =
    lines.reversed().map { TextLine(it.text, -it.right, -it.bottom, -it.left, -it.top) }
