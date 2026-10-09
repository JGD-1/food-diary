package nl.guido.foodtracker.feature.sync.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.feature.sync.engine.SyncOwner
import nl.guido.foodtracker.feature.sync.engine.SyncTables
import nl.guido.foodtracker.feature.sync.local.RoomLocalStore
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Writes the export zip to a file the person picked (Downloads, Drive, …). */
class Exporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionRepository,
    private val local: RoomLocalStore,
) {
    fun suggestedFileName(today: LocalDate = LocalDate.now()) = "food-diary-export-$today.zip"

    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val user = session.currentUser.value
            val owner = SyncOwner(user.userId, user.householdId)
            val tables = SyncTables.all.filter { local.exists(it) }.map { it to local.allOf(it, owner) }
            val files = Export.files(tables, Instant.now())
            context.contentResolver.openOutputStream(uri)?.use { Export.zip(files, it) } ?: error("no file")
        }.isSuccess
    }
}
