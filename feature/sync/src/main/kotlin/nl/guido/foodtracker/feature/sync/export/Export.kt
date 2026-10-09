package nl.guido.foodtracker.feature.sync.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.guido.foodtracker.feature.sync.engine.Row
import nl.guido.foodtracker.feature.sync.engine.SyncTable
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * "Export all my data": one zip with data.json (everything) and a CSV file per table
 * that opens in a spreadsheet. Deleted rows are left out.
 */
object Export {
    private val pretty = Json { prettyPrint = true }

    fun files(tables: List<Pair<SyncTable, List<Row>>>, exportedAt: Instant): Map<String, String> = buildMap {
        put("data.json", json(tables, exportedAt))
        tables.forEach { (table, rows) -> put("${table.local}.csv", csv(table, rows)) }
    }

    fun json(tables: List<Pair<SyncTable, List<Row>>>, exportedAt: Instant): String = pretty.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "app" to JsonPrimitive("Food diary"),
                "exportedAt" to JsonPrimitive(exportedAt.toString()),
                "tables" to JsonObject(
                    tables.associate { (table, rows) -> table.local to JsonArray(rows.map { it.toJson() }) },
                ),
            ),
        ),
    )

    fun csv(table: SyncTable, rows: List<Row>): String {
        val columns = table.columns.map { it.local }.filter { it != "deleted" }
        // Diary lines get a readable "name" next to the technical "whatJson".
        val withName = "whatJson" in columns
        val header = (if (withName) listOf("name") else emptyList()) + columns
        return buildString {
            appendLine(header.joinToString(",") { cell(it) })
            for (row in rows) {
                val name = if (withName) listOf(nameOf(row["whatJson"] as String?)) else emptyList()
                appendLine((name + columns.map { row[it] }).joinToString(",") { cell(it) })
            }
        }
    }

    fun zip(files: Map<String, String>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun nameOf(whatJson: String?): String? = whatJson?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject["name"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    }

    private fun cell(value: Any?): String {
        val s = when (value) {
            null -> ""
            is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
            else -> value.toString()
        }
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun Row.toJson() = JsonObject(
        filterKeys { it != "deleted" }.mapValues { (_, v) ->
            when (v) {
                null -> JsonNull
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        },
    )
}
