package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The label reader on exactly what the phone's text reader (ML Kit) read from Guido's label photos.
 * The lines come from LabelPhotosTest on the emulator (its log, tag "LabelLines"), saved per photo in
 * label-lines/<photo>.txt as "turn|left|top|right|bottom|text"; turn 180 = read turned around.
 * The right values are in labels/expected.csv, shared with LabelPhotosTest.
 */
class LabelPhotoLinesTest {
    private fun resource(path: String) = javaClass.classLoader!!.getResource(path)

    @Test fun `every label photo reads right`() {
        val expected = resource("labels/expected.csv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("file,") }
        val report = expected.mapNotNull { row ->
            val file = row.substringBefore(',')
            val saved = resource("label-lines/${file.removeSuffix(".jpg")}.txt") ?: return@mapNotNull null
            val lines = saved.readText().lines().filter { it.isNotBlank() }.map { it.split('|', limit = 6) }
            fun read(turn: String) = lines.filter { it[0] == turn }
                .map { TextLine(it[5], it[1].toFloat(), it[2].toFloat(), it[3].toFloat(), it[4].toFloat()) }
            val got = NutritionLabelParser.parse(readLabelText(read("0")) { read("180") })
            val misses = misses(row, got)
            if (misses.isEmpty()) "OK $file" else "MISS $file ${misses.joinToString(" ")}"
        }
        val wrong = report.filter { it.startsWith("MISS") }
        assertEquals("Read right: ${report.size - wrong.size} of ${report.size}\n${report.joinToString("\n")}", emptyList<String>(), wrong)
    }

    /** "kcal=150.0/read 620.0" for each value that differs; empty when all right. */
    private fun misses(row: String, got: LabelValues): List<String> {
        val c = row.split(',')
        fun want(i: Int) = c.getOrNull(i)?.takeIf { it.isNotBlank() }?.toDouble()
        val wrong = listOf(
            "kcal" to (want(1) to got.kcal), "fat" to (want(2) to got.fat), "carbs" to (want(3) to got.carbs),
            "protein" to (want(4) to got.protein), "sugar" to (want(5) to got.sugar), "fibre" to (want(6) to got.fibre),
            "salt" to (want(7) to got.salt),
        ).filter { (_, v) -> v.first != v.second }.map { (name, v) -> "$name=${v.first}/read ${v.second}" }
        val perMl = c.getOrNull(8) == "yes"
        return if (perMl != got.perMl) wrong + "perMl=$perMl" else wrong
    }
}
