package nl.guido.foodtracker.feature.sync

import nl.guido.foodtracker.feature.sync.engine.SyncTables
import nl.guido.foodtracker.feature.sync.export.Export
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipInputStream

class ExportTest {
    private val entry = mapOf(
        "id" to "e1", "userId" to "u1", "date" to "2026-10-09", "meal" to "DINNER",
        "whatJson" to """{"type":"food","foodId":"f1","name":"Pasta, \"al dente\""}""",
        "portionGrams" to 250.0, "portionLabel" to null, "kcal" to 400.5, "protein" to 20.0, "carbs" to 30.0,
        "fat" to 10.0, "isEstimate" to false, "createdAt" to 5L, "updatedAt" to 6L, "deleted" to false,
    )

    @Test
    fun `diary CSV has a readable name column and escapes quotes`() {
        val csv = Export.csv(SyncTables.logEntry, listOf(entry)).lines()
        assertTrue(csv[0].startsWith("name,id,userId,date,meal"))
        assertTrue(csv[1].startsWith("\"Pasta, \"\"al dente\"\"\",e1,u1,2026-10-09,DINNER"))
        assertTrue(csv[1].contains(",250,,400.5,"))
    }

    @Test
    fun `zip holds data json and one CSV per table`() {
        val files = Export.files(listOf(SyncTables.logEntry to listOf(entry), SyncTables.weighIn to emptyList()), Instant.EPOCH)
        val out = ByteArrayOutputStream()
        Export.zip(files, out)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) names += (zip.nextEntry ?: break).name
        }
        assertEquals(listOf("data.json", "log_entry.csv", "weigh_in.csv"), names)
        assertTrue(files.getValue("data.json").contains("\"kcal\": 400.5"))
    }
}
