package nl.guido.foodtracker.feature.sync.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.guido.foodtracker.feature.sync.engine.SyncMarks
import nl.guido.foodtracker.feature.sync.engine.SyncTable
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in account, as remembered on this phone. */
data class StoredAccount(
    val userId: String,
    val email: String?,
    val displayName: String?,
    val householdId: String,
    val inviteCode: String,
    val memberCount: Int,
    val accessToken: String,
    val refreshToken: String,
    /** When [accessToken] stops working (ms). */
    val expiresAt: Long,
)

/** Small key-value storage on the phone for the account, sign-in in progress and sync progress. */
@Singleton
class SyncPrefs @Inject constructor(@ApplicationContext context: Context) : SyncMarks {
    private val account = context.getSharedPreferences("sync_account", Context.MODE_PRIVATE)
    private val marks = context.getSharedPreferences("sync_marks", Context.MODE_PRIVATE)

    fun loadAccount(): StoredAccount? {
        val userId = account.getString("userId", null) ?: return null
        return StoredAccount(
            userId = userId,
            email = account.getString("email", null),
            displayName = account.getString("displayName", null),
            householdId = account.getString("householdId", null) ?: return null,
            inviteCode = account.getString("inviteCode", "") ?: "",
            memberCount = account.getInt("memberCount", 1),
            accessToken = account.getString("accessToken", null) ?: return null,
            refreshToken = account.getString("refreshToken", null) ?: return null,
            expiresAt = account.getLong("expiresAt", 0),
        )
    }

    fun saveAccount(a: StoredAccount) {
        account.edit()
            .putString("userId", a.userId)
            .putString("email", a.email)
            .putString("displayName", a.displayName)
            .putString("householdId", a.householdId)
            .putString("inviteCode", a.inviteCode)
            .putInt("memberCount", a.memberCount)
            .putString("accessToken", a.accessToken)
            .putString("refreshToken", a.refreshToken)
            .putLong("expiresAt", a.expiresAt)
            .apply()
    }

    var pkceVerifier: String?
        get() = account.getString("pkceVerifier", null)
        set(value) = account.edit().putString("pkceVerifier", value).apply()

    var signInOfferDismissed: Boolean
        get() = account.getBoolean("signInOfferDismissed", false)
        set(value) = account.edit().putBoolean("signInOfferDismissed", value).apply()

    var lastSyncedAt: Long
        get() = marks.getLong("lastSyncedAt", 0)
        set(value) = marks.edit().putLong("lastSyncedAt", value).apply()

    override fun pushedUpTo(table: SyncTable) = marks.getLong("push.${table.local}", 0)
    override fun setPushedUpTo(table: SyncTable, updatedAt: Long) = marks.edit().putLong("push.${table.local}", updatedAt).apply()
    override fun pulledUpTo(table: SyncTable): String? = marks.getString("pull.${table.local}", null)
    override fun setPulledUpTo(table: SyncTable, serverTime: String) =
        marks.edit().putString("pull.${table.local}", serverTime).apply()

    fun forgetPulls(tables: List<SyncTable>) {
        marks.edit().apply { tables.forEach { remove("pull.${it.local}") } }.apply()
    }
}
