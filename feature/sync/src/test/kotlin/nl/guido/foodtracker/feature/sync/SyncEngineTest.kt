package nl.guido.foodtracker.feature.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import nl.guido.foodtracker.feature.sync.engine.LocalStore
import nl.guido.foodtracker.feature.sync.engine.RejectedRowException
import nl.guido.foodtracker.feature.sync.engine.RemoteStore
import nl.guido.foodtracker.feature.sync.engine.Row
import nl.guido.foodtracker.feature.sync.engine.Scope
import nl.guido.foodtracker.feature.sync.engine.SyncEngine
import nl.guido.foodtracker.feature.sync.engine.SyncMarks
import nl.guido.foodtracker.feature.sync.engine.SyncOwner
import nl.guido.foodtracker.feature.sync.engine.SyncTable
import nl.guido.foodtracker.feature.sync.engine.SyncTables
import nl.guido.foodtracker.feature.sync.engine.id
import nl.guido.foodtracker.feature.sync.engine.toLocal
import nl.guido.foodtracker.feature.sync.engine.updatedAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

private val me = SyncOwner("u1", "h1")
private val weighIns = SyncTables.weighIn
private val recipes = SyncTables.recipe

private fun weighIn(id: String, user: String = "u1", kg: Double = 80.0, at: Long = 1, deleted: Boolean = false): Row =
    mapOf("id" to id, "userId" to user, "date" to "2026-10-09", "kg" to kg, "updatedAt" to at, "deleted" to deleted)

private class FakeLocal : LocalStore {
    val tables = mutableMapOf<String, MutableMap<String, Row>>()
    override suspend fun exists(table: SyncTable) = true
    override suspend fun changedSince(table: SyncTable, owner: SyncOwner, since: Long) =
        tables[table.local].orEmpty().values.filter {
            it.updatedAt >= since && when (table.scope) {
                Scope.USER -> it[table.ownerColumn] == owner.userId
                else -> it[table.ownerColumn] == owner.householdId
            }
        }
    override suspend fun updatedAt(table: SyncTable, ids: Collection<String>) =
        tables[table.local].orEmpty().filterKeys { it in ids }.mapValues { it.value.updatedAt }
    override suspend fun write(table: SyncTable, rows: List<Row>) {
        rows.forEach { tables.getOrPut(table.local) { mutableMapOf() }[it.id] = it }
    }
    fun put(table: SyncTable, row: Row) = tables.getOrPut(table.local) { mutableMapOf() }.set(row.id, row)
}

/** Behaves like the Supabase tables: newest updatedAt wins, server time on every accepted write. */
private class FakeRemote : RemoteStore {
    val rows = mutableMapOf<String, MutableMap<String, JsonObject>>()
    var clock = OffsetDateTime.parse("2026-10-09T10:00:00Z")
    var rejectIds = setOf<String>()
    var upsertCalls = 0

    override suspend fun upsert(table: SyncTable, rows: List<JsonObject>) {
        upsertCalls++
        if (rows.any { (it["id"] as JsonPrimitive).content in rejectIds }) throw RejectedRowException("no")
        val stored = this.rows.getOrPut(table.remote) { mutableMapOf() }
        for (row in rows) {
            val id = (row["id"] as JsonPrimitive).content
            val old = stored[id]
            if (old != null && row.at() <= old.at()) continue
            clock = clock.plusSeconds(1)
            stored[id] = JsonObject(row + (SyncEngine.SERVER_TIME to JsonPrimitive(clock.toString())))
        }
    }

    override suspend fun changedAfter(table: SyncTable, after: String?, limit: Int) =
        rows[table.remote].orEmpty().values
            .sortedBy { OffsetDateTime.parse(it.serverTime()) }
            .filter { after == null || OffsetDateTime.parse(it.serverTime()).isAfter(OffsetDateTime.parse(after)) }
            .take(limit)

    private fun JsonObject.at() = (this["updated_at"] as JsonPrimitive).long
    private fun JsonObject.serverTime() = (this[SyncEngine.SERVER_TIME] as JsonPrimitive).content
}

private class MemoryMarks : SyncMarks {
    val push = mutableMapOf<String, Long>()
    val pull = mutableMapOf<String, String>()
    override fun pushedUpTo(table: SyncTable) = push[table.local] ?: 0
    override fun setPushedUpTo(table: SyncTable, updatedAt: Long) { push[table.local] = updatedAt }
    override fun pulledUpTo(table: SyncTable) = pull[table.local]
    override fun setPulledUpTo(table: SyncTable, serverTime: String) { pull[table.local] = serverTime }
}

class SyncEngineTest {
    private val remote = FakeRemote()

    private fun phone(): Pair<FakeLocal, SyncEngine> {
        val local = FakeLocal()
        return local to SyncEngine(local, remote, MemoryMarks(), pageSize = 2)
    }

    @Test
    fun `a weigh-in made on one phone arrives on another`() = runTest {
        val (a, engineA) = phone()
        val (b, engineB) = phone()
        a.put(weighIns, weighIn("w1", kg = 81.5))
        engineA.sync(me, listOf(weighIns))
        engineB.sync(me, listOf(weighIns))
        assertEquals(81.5, b.tables["weigh_in"]!!["w1"]!!["kg"])
    }

    @Test
    fun `only my own rows are pushed`() = runTest {
        val (a, engine) = phone()
        a.put(weighIns, weighIn("mine"))
        a.put(weighIns, weighIn("partners", user = "u2"))
        val result = engine.sync(me, listOf(weighIns))
        assertEquals(1, result.pushed)
        assertEquals(setOf("mine"), remote.rows["weigh_ins"]!!.keys)
    }

    @Test
    fun `the newest change wins on both sides`() = runTest {
        val (a, engineA) = phone()
        val (b, engineB) = phone()
        a.put(weighIns, weighIn("w1", kg = 80.0, at = 100))
        engineA.sync(me, listOf(weighIns))
        engineB.sync(me, listOf(weighIns))
        // B edits later, A edits earlier but syncs last: B's newer value must survive everywhere.
        b.put(weighIns, weighIn("w1", kg = 79.0, at = 300))
        a.put(weighIns, weighIn("w1", kg = 78.0, at = 200))
        engineB.sync(me, listOf(weighIns))
        engineA.sync(me, listOf(weighIns))
        assertEquals(79.0, a.tables["weigh_in"]!!["w1"]!!["kg"])
        engineB.sync(me, listOf(weighIns))
        assertEquals(79.0, b.tables["weigh_in"]!!["w1"]!!["kg"])
    }

    @Test
    fun `deletions travel as a flag`() = runTest {
        val (a, engineA) = phone()
        val (b, engineB) = phone()
        a.put(weighIns, weighIn("w1", at = 1))
        engineA.sync(me, listOf(weighIns))
        engineB.sync(me, listOf(weighIns))
        a.put(weighIns, weighIn("w1", at = 2, deleted = true))
        engineA.sync(me, listOf(weighIns))
        engineB.sync(me, listOf(weighIns))
        assertEquals(true, b.tables["weigh_in"]!!["w1"]!!["deleted"])
    }

    @Test
    fun `household recipes are shared with the partner`() = runTest {
        val (mine, engineMine) = phone()
        val (partner, enginePartner) = phone()
        mine.put(
            recipes,
            mapOf(
                "id" to "r1", "householdId" to "h1", "name" to "Chili", "ingredientsJson" to "[]",
                "pinned" to false, "usualPortionJson" to null, "updatedAt" to 5L, "deleted" to false,
            ),
        )
        engineMine.sync(me, listOf(recipes))
        enginePartner.sync(SyncOwner("u2", "h1"), listOf(recipes))
        assertEquals("Chili", partner.tables["recipe"]!!["r1"]!!["name"])
        assertNull(partner.tables["recipe"]!!["r1"]!!["usualPortionJson"])
    }

    @Test
    fun `many rows are sent and fetched in pages`() = runTest {
        val (a, engineA) = phone()
        val (b, engineB) = phone()
        (1..5).forEach { a.put(weighIns, weighIn("w$it", at = it.toLong())) }
        engineA.sync(me, listOf(weighIns))
        assertEquals(3, remote.upsertCalls)
        val result = engineB.sync(me, listOf(weighIns))
        assertEquals(5, result.pulled)
        assertEquals(5, b.tables["weigh_in"]!!.size)
    }

    @Test
    fun `a row the server refuses is skipped and the rest still syncs`() = runTest {
        val (a, engine) = phone()
        a.put(weighIns, weighIn("good", at = 1))
        a.put(weighIns, weighIn("bad", at = 2))
        remote.rejectIds = setOf("bad")
        val result = engine.sync(me, listOf(weighIns))
        assertEquals(1, result.pushed)
        assertEquals(1, result.skipped)
        assertEquals(setOf("good"), remote.rows["weigh_ins"]!!.keys)
    }

    @Test
    fun `nothing changed means nothing pulled`() = runTest {
        val (a, engine) = phone()
        a.put(weighIns, weighIn("w1"))
        engine.sync(me, listOf(weighIns))
        val again = engine.sync(me, listOf(weighIns))
        assertEquals(0, again.pulled)
    }

    @Test
    fun `remote rows convert to phone rows`() {
        val json = JsonObject(
            mapOf(
                "id" to JsonPrimitive("w1"), "user_id" to JsonPrimitive("u1"), "date" to JsonPrimitive("2026-10-09"),
                "kg" to JsonPrimitive(80), "updated_at" to JsonPrimitive(7), "deleted" to JsonPrimitive(false),
            ),
        )
        assertEquals(weighIn("w1", at = 7), weighIns.toLocal(json))
    }
}
