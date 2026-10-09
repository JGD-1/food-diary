package nl.guido.foodtracker.feature.sync.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.data.db.FoodDatabase
import nl.guido.foodtracker.feature.sync.engine.ColType
import nl.guido.foodtracker.feature.sync.engine.LocalStore
import nl.guido.foodtracker.feature.sync.engine.Row
import nl.guido.foodtracker.feature.sync.engine.Scope
import nl.guido.foodtracker.feature.sync.engine.SyncOwner
import nl.guido.foodtracker.feature.sync.engine.SyncTable
import nl.guido.foodtracker.feature.sync.engine.SyncTables
import nl.guido.foodtracker.feature.sync.engine.asBoolean
import nl.guido.foodtracker.feature.sync.engine.id
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the shared phone database (core/data) directly with SQL, table by table,
 * using the column lists in [SyncTables]. Agreed with the lead: no schema changes from here.
 */
@Singleton
class RoomLocalStore @Inject constructor(private val database: FoodDatabase) : LocalStore {

    private val db: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    override suspend fun exists(table: SyncTable): Boolean = io {
        db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table.local))
            .use { it.moveToFirst() }
    }

    override suspend fun changedSince(table: SyncTable, owner: SyncOwner, since: Long): List<Row> =
        rows(table, "updatedAt >= ?", listOf(since), owner)

    /** Everything of this person in [table] that isn't deleted (for the export). */
    suspend fun allOf(table: SyncTable, owner: SyncOwner): List<Row> = rows(table, "deleted = 0", emptyList(), owner)

    private suspend fun rows(table: SyncTable, where: String, args: List<Any>, owner: SyncOwner): List<Row> = io {
        val (scopeSql, scopeArg) = when (table.scope) {
            Scope.USER -> "${table.ownerColumn} = ?" to owner.userId
            Scope.HOUSEHOLD -> "${table.ownerColumn} = ?" to owner.householdId
            Scope.HOUSEHOLD_VARIANT ->
                "${table.ownerColumn} IN (SELECT id FROM recipe WHERE householdId = ?)" to owner.householdId
        }
        val conditions = listOfNotNull(where, scopeSql, table.localFilter).joinToString(" AND ") { "($it)" }
        val columns = table.columns.joinToString { it.local }
        db.query("SELECT $columns FROM ${table.local} WHERE $conditions ORDER BY updatedAt", (args + scopeArg).toTypedArray())
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toRow(table)) } }
    }

    override suspend fun updatedAt(table: SyncTable, ids: Collection<String>): Map<String, Long> = io {
        ids.chunked(500).flatMap { chunk ->
            val marks = chunk.joinToString { "?" }
            db.query("SELECT id, updatedAt FROM ${table.local} WHERE id IN ($marks)", chunk.toTypedArray())
                .use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getLong(1)) } }
        }.toMap()
    }

    override suspend fun write(table: SyncTable, rows: List<Row>) = io {
        db.beginTransaction()
        try {
            for (row in rows) {
                val values = row.toValues(table)
                // UPDATE then INSERT: Android 10 has no SQLite "upsert", and REPLACE would wipe columns we don't know.
                if (db.update(table.local, SQLiteDatabase.CONFLICT_NONE, values, "id = ?", arrayOf(row.id)) == 0) {
                    db.insert(table.local, SQLiteDatabase.CONFLICT_REPLACE, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.invalidationTracker.refreshVersionsAsync()
    }

    /**
     * After the first sign-in: rows made on this phone before signing in move to the real account,
     * so nothing logged earlier is lost.
     */
    suspend fun adopt(fromUser: String, toUser: String, fromHousehold: String, toHousehold: String) = io {
        db.beginTransaction()
        try {
            for (table in SyncTables.all) {
                if (!tableExists(table.local)) continue
                val (from, to) = when (table.scope) {
                    Scope.USER -> fromUser to toUser
                    Scope.HOUSEHOLD -> fromHousehold to toHousehold
                    Scope.HOUSEHOLD_VARIANT -> continue
                }
                db.execSQL("UPDATE ${table.local} SET ${table.ownerColumn} = ? WHERE ${table.ownerColumn} = ?", arrayOf(to, from))
            }
            for (table in extraUserTables) {
                if (tableExists(table)) db.execSQL("UPDATE $table SET userId = ? WHERE userId = ?", arrayOf(toUser, fromUser))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.invalidationTracker.refreshVersionsAsync()
    }

    /** Joined another household: show its recipes and keep ours with us. */
    suspend fun moveHousehold(from: String, to: String) = io {
        for (table in SyncTables.household) {
            if (table.scope == Scope.HOUSEHOLD && tableExists(table.local)) {
                db.execSQL("UPDATE ${table.local} SET householdId = ? WHERE householdId = ?", arrayOf(to, from))
            }
        }
        database.invalidationTracker.refreshVersionsAsync()
    }

    private fun tableExists(name: String) =
        db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        /** Private tables that don't sync yet but are owned by a user id. */
        val extraUserTables = listOf("favourite")
    }
}

private fun Cursor.toRow(table: SyncTable): Row = table.columns.withIndex().associate { (i, col) ->
    col.local to when {
        isNull(i) -> null
        col.type == ColType.TEXT -> getString(i)
        col.type == ColType.LONG -> getLong(i)
        col.type == ColType.DOUBLE -> getDouble(i)
        else -> getLong(i) != 0L
    }
}

private fun Row.toValues(table: SyncTable) = ContentValues().apply {
    for (col in table.columns) {
        val value = this@toValues[col.local]
        when {
            value == null -> putNull(col.local)
            col.type == ColType.TEXT -> put(col.local, value.toString())
            col.type == ColType.LONG -> put(col.local, (value as Number).toLong())
            col.type == ColType.DOUBLE -> put(col.local, (value as Number).toDouble())
            else -> put(col.local, if (value.asBoolean()) 1 else 0)
        }
    }
}
