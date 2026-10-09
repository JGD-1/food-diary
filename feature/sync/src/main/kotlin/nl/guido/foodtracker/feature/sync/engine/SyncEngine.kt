package nl.guido.foodtracker.feature.sync.engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.OffsetDateTime

/** Who is signed in; decides which phone rows are theirs to push. */
data class SyncOwner(val userId: String, val householdId: String)

/** The phone database, as seen by sync. */
interface LocalStore {
    suspend fun exists(table: SyncTable): Boolean

    /** This person's rows with updatedAt ≥ [since], deleted ones included. */
    suspend fun changedSince(table: SyncTable, owner: SyncOwner, since: Long): List<Row>

    /** updatedAt of the given ids that exist on the phone. */
    suspend fun updatedAt(table: SyncTable, ids: Collection<String>): Map<String, Long>

    /** Insert or replace these rows (all columns of [table]). */
    suspend fun write(table: SyncTable, rows: List<Row>)
}

/** The online copy (Supabase). */
interface RemoteStore {
    suspend fun upsert(table: SyncTable, rows: List<JsonObject>)

    /** Rows changed after [after] (server time, ISO), oldest first. */
    suspend fun changedAfter(table: SyncTable, after: String?, limit: Int): List<JsonObject>
}

/** Remembers how far each table has been pushed and pulled. */
interface SyncMarks {
    fun pushedUpTo(table: SyncTable): Long
    fun setPushedUpTo(table: SyncTable, updatedAt: Long)
    fun pulledUpTo(table: SyncTable): String?
    fun setPulledUpTo(table: SyncTable, serverTime: String)
}

/** A row the server would not take (e.g. it belongs to someone else); skipped so the rest still syncs. */
class RejectedRowException(message: String) : Exception(message)

data class SyncResult(val pushed: Int, val pulled: Int, val skipped: Int)

/**
 * Offline-first sync: push this phone's changes, then pull everyone else's.
 * Every row has an id, updatedAt and a deleted flag; the newest updatedAt wins, on the server
 * (see supabase/migrations) and here.
 */
class SyncEngine(
    private val local: LocalStore,
    private val remote: RemoteStore,
    private val marks: SyncMarks,
    private val pageSize: Int = 200,
) {
    suspend fun sync(owner: SyncOwner, tables: List<SyncTable> = SyncTables.all): SyncResult {
        var pushed = 0
        var pulled = 0
        var skipped = 0
        val present = tables.filter { local.exists(it) }
        for (table in present) {
            val (ok, bad) = push(table, owner)
            pushed += ok
            skipped += bad
        }
        for (table in present) pulled += pull(table)
        return SyncResult(pushed, pulled, skipped)
    }

    private suspend fun push(table: SyncTable, owner: SyncOwner): Pair<Int, Int> {
        // ≥ rather than >: a row saved in the same millisecond as the last push is sent again
        // instead of being missed. The server ignores the repeat.
        val rows = local.changedSince(table, owner, marks.pushedUpTo(table)).sortedBy { it.updatedAt }
        var ok = 0
        var bad = 0
        for (chunk in rows.chunked(pageSize)) {
            try {
                remote.upsert(table, chunk.map { table.toRemote(it) })
                ok += chunk.size
            } catch (e: RejectedRowException) {
                // One bad row rejects the whole request: send them one by one and skip the bad ones.
                for (row in chunk) {
                    try {
                        remote.upsert(table, listOf(table.toRemote(row)))
                        ok++
                    } catch (e: RejectedRowException) {
                        bad++
                    }
                }
            }
            marks.setPushedUpTo(table, chunk.last().updatedAt)
        }
        return ok to bad
    }

    private suspend fun pull(table: SyncTable): Int {
        var applied = 0
        val start = marks.pulledUpTo(table)
        // Look back a minute: a slow save on the server can get an earlier time than one we already saw.
        var after = start?.let { OffsetDateTime.parse(it).minusSeconds(OVERLAP_SECONDS).toString() }
        var newest = start
        while (true) {
            val page = remote.changedAfter(table, after, pageSize)
            if (page.isEmpty()) break
            val incoming = page.map { table.toLocal(it) }
            val mine = local.updatedAt(table, incoming.map { it.id })
            val newer = incoming.filter { row -> mine[row.id]?.let { row.updatedAt > it } ?: true }
            if (newer.isNotEmpty()) local.write(table, newer)
            applied += newer.size
            val last = (page.last()[SERVER_TIME] as JsonPrimitive).content
            if (newest == null || OffsetDateTime.parse(last).isAfter(OffsetDateTime.parse(newest))) newest = last
            after = last
            if (page.size < pageSize) break
        }
        if (newest != null && newest != start) marks.setPulledUpTo(table, newest)
        return applied
    }

    companion object {
        const val SERVER_TIME = "server_updated_at"
        const val OVERLAP_SECONDS = 60L
    }
}
