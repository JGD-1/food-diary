package nl.guido.foodtracker.feature.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.room.InvalidationTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import nl.guido.foodtracker.core.data.db.FoodDatabase
import nl.guido.foodtracker.core.data.repo.CurrentUser
import nl.guido.foodtracker.feature.sync.engine.RemoteStore
import nl.guido.foodtracker.feature.sync.engine.SyncEngine
import nl.guido.foodtracker.feature.sync.engine.SyncOwner
import nl.guido.foodtracker.feature.sync.engine.SyncTable
import nl.guido.foodtracker.feature.sync.engine.SyncTables
import nl.guido.foodtracker.feature.sync.local.RoomLocalStore
import nl.guido.foodtracker.feature.sync.local.StoredAccount
import nl.guido.foodtracker.feature.sync.local.SyncPrefs
import nl.guido.foodtracker.feature.sync.remote.AuthTokens
import nl.guido.foodtracker.feature.sync.remote.HouseholdInfo
import nl.guido.foodtracker.feature.sync.remote.Pkce
import nl.guido.foodtracker.feature.sync.remote.SignedOutException
import nl.guido.foodtracker.feature.sync.remote.SupabaseApi
import nl.guido.foodtracker.feature.sync.remote.SupabaseConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The user before signing in. Same ids as the old stand-in, so rows made then are adopted at sign-in. */
internal val LOCAL_USER = CurrentUser(userId = "local-user", householdId = "local-household", displayName = "Me")

const val AUTH_REDIRECT = "nl.guido.foodtracker://auth-callback"

/** What the account screen shows. */
data class AccountState(
    val configured: Boolean,
    val account: StoredAccount?,
    val syncing: Boolean = false,
    val signingIn: Boolean = false,
    val lastSyncedAt: Long = 0,
    /** The last attempt couldn't reach the internet; everything stays saved on the phone. */
    val offline: Boolean = false,
    val problem: Problem? = null,
) {
    val signedIn: Boolean get() = account != null
}

enum class Problem { SIGN_IN_FAILED, CODE_NOT_FOUND, SIGNED_OUT }

/**
 * Owns the account and runs sync: when the app starts, a few seconds after something changes,
 * when the internet comes back, and every 15 minutes while the app is open.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: SupabaseConfig,
    private val api: SupabaseApi,
    private val prefs: SyncPrefs,
    private val local: RoomLocalStore,
    private val database: FoodDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var started = false

    private val _state = MutableStateFlow(
        AccountState(configured = config.isSet, account = prefs.loadAccount(), lastSyncedAt = prefs.lastSyncedAt),
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Starts the background triggers once. Safe to call many times. */
    @OptIn(FlowPreview::class)
    @Synchronized
    fun start() {
        if (started || !config.isSet) return
        started = true
        scope.launch { requests.consumeAsFlow().debounce(CHANGE_DELAY_MS).collect { syncNow() } }
        scope.launch {
            while (true) {
                syncNow()
                delay(PERIOD_MS)
            }
        }
        scope.launch {
            // Only tables that exist can be watched.
            val present = SyncTables.all.filter { local.exists(it) }.map { it.local }.toTypedArray()
            if (present.isNotEmpty()) {
                database.invalidationTracker.addObserver(object : InvalidationTracker.Observer(present) {
                    override fun onInvalidated(tables: Set<String>) {
                        requests.trySend(Unit)
                    }
                })
            }
        }
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    requests.trySend(Unit)
                }
            },
        )
    }

    // Sign-in -------------------------------------------------------------------------------------

    /** Address to open in the browser. Remembers the secret half so only this app can finish. */
    fun beginSignIn(): String {
        val verifier = Pkce.newVerifier()
        prefs.pkceVerifier = verifier
        _state.update { it.copy(problem = null) }
        return api.googleSignInUrl(AUTH_REDIRECT, Pkce.challenge(verifier))
    }

    /** The browser came back with [code] (or an error). */
    fun finishSignIn(code: String?) {
        val verifier = prefs.pkceVerifier
        if (code == null || verifier == null) {
            _state.update { it.copy(problem = Problem.SIGN_IN_FAILED) }
            return
        }
        scope.launch {
            _state.update { it.copy(signingIn = true, problem = null) }
            try {
                val tokens = api.exchangeCode(code, verifier)
                val user = tokens.user ?: throw IOException("no user")
                val household = api.ensureHousehold(tokens.accessToken)
                local.adopt(LOCAL_USER.userId, user.id, LOCAL_USER.householdId, household.householdId)
                val account = StoredAccount(
                    userId = user.id,
                    email = user.email,
                    displayName = user.metadata.name(),
                    householdId = household.householdId,
                    inviteCode = household.inviteCode,
                    memberCount = household.memberCount,
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresAt = expiry(tokens),
                )
                prefs.saveAccount(account)
                prefs.pkceVerifier = null
                _state.update { it.copy(account = account, signingIn = false) }
                start()
                syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline, or an answer the app didn't understand: never crash, just let them try again.
                _state.update { it.copy(signingIn = false, problem = Problem.SIGN_IN_FAILED) }
            }
        }
    }

    /** Share recipes with the household whose code this is. */
    suspend fun joinHousehold(code: String): Boolean = mutex.withLock {
        try {
            val account = freshAccount() ?: return@withLock false
            val info = api.joinHousehold(account.accessToken, code.trim())
            applyHousehold(account, info)
            true
        } catch (e: IOException) {
            _state.update { it.copy(problem = if (e is SignedOutException) Problem.SIGNED_OUT else Problem.CODE_NOT_FOUND) }
            false
        }
    }.also { if (it) syncNow() }

    private suspend fun applyHousehold(account: StoredAccount, info: HouseholdInfo) {
        if (info.householdId != account.householdId) {
            // Recipes made alone come along (the server moved them too); then fetch the new household's recipes.
            if (info.movedFrom != null) local.moveHousehold(account.householdId, info.householdId)
            prefs.forgetPulls(SyncTables.household)
        }
        val updated = account.copy(householdId = info.householdId, inviteCode = info.inviteCode, memberCount = info.memberCount)
        prefs.saveAccount(updated)
        _state.update { it.copy(account = updated, problem = null) }
    }

    // Sync ----------------------------------------------------------------------------------------

    fun requestSync() {
        requests.trySend(Unit)
    }

    suspend fun syncNow() {
        if (!config.isSet || prefs.loadAccount() == null) return
        mutex.withLock {
            _state.update { it.copy(syncing = true) }
            try {
                val account = freshAccount() ?: return@withLock
                // Pick up a partner joining (member count) before syncing recipes.
                runCatching { api.ensureHousehold(account.accessToken) }.getOrNull()?.let { applyHousehold(account, it) }
                val current = prefs.loadAccount() ?: return@withLock
                engineFor(current).sync(SyncOwner(current.userId, current.householdId))
                val now = System.currentTimeMillis()
                prefs.lastSyncedAt = now
                _state.update { it.copy(lastSyncedAt = now, offline = false) }
            } catch (e: SignedOutException) {
                _state.update { it.copy(problem = Problem.SIGNED_OUT) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline, or an unexpected answer from the server: everything stays on the phone, try later.
                _state.update { it.copy(offline = true) }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    /** The account with a working access token, refreshing it when it's (nearly) expired. */
    private suspend fun freshAccount(): StoredAccount? {
        val account = prefs.loadAccount() ?: return null
        if (System.currentTimeMillis() < account.expiresAt - REFRESH_MARGIN_MS) return account
        val tokens = api.refresh(account.refreshToken)
        return account.copy(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken, expiresAt = expiry(tokens))
            .also { prefs.saveAccount(it) }
    }

    private fun engineFor(account: StoredAccount): SyncEngine {
        val remote = object : RemoteStore {
            override suspend fun upsert(table: SyncTable, rows: List<kotlinx.serialization.json.JsonObject>) =
                api.upsert(account.accessToken, table, rows)

            override suspend fun changedAfter(table: SyncTable, after: String?, limit: Int) =
                api.changedAfter(account.accessToken, table, after, limit)
        }
        return SyncEngine(local, remote, prefs)
    }

    private fun expiry(tokens: AuthTokens) = System.currentTimeMillis() + tokens.expiresIn * 1000

    private fun Map<String, kotlinx.serialization.json.JsonElement>.name(): String? =
        listOf("full_name", "name").firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull }

    private companion object {
        const val CHANGE_DELAY_MS = 5_000L
        const val PERIOD_MS = 15 * 60_000L
        const val REFRESH_MARGIN_MS = 60_000L
    }
}
