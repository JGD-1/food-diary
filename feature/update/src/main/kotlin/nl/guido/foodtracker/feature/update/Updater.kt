package nl.guido.foodtracker.feature.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Where the updater is; one shared state for the card on start-up and Profile > Updates. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val remote: RemoteVersion) : UpdateState
    data class Downloading(val remote: RemoteVersion, val progress: Float) : UpdateState
    /** Downloaded and checked; Android's install screen can open. */
    data class Ready(val remote: RemoteVersion) : UpdateState
    data class Failed(val problem: UpdateProblem) : UpdateState
    data class BadDownload(val result: VerifyResult) : UpdateState
}

/** Saves the server address and the last automatic check on the phone (not synced). */
@Singleton
class UpdateStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _server = MutableStateFlow(readServer())
    val server: StateFlow<ServerAddress> = _server.asStateFlow()

    fun saveServer(value: ServerAddress) {
        prefs.edit().putString(KEY_SERVER, value.toString()).apply()
        _server.value = value
    }

    var lastAutoCheck: LocalDate?
        get() = prefs.getString(KEY_LAST_CHECK, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_LAST_CHECK, value?.toString()).apply()

    private fun readServer(): ServerAddress =
        prefs.getString(KEY_SERVER, null)?.let(UpdateLogic::parseAddress) ?: ServerAddress.DEFAULT

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_LAST_CHECK = "last_auto_check"
    }
}

@Singleton
class Updater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: UpdateStore,
    private val server: HomeServer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    /** "Later" on the start-up card: hidden until the next check finds something. */
    private val _cardDismissed = MutableStateFlow(false)
    val cardDismissed: StateFlow<Boolean> = _cardDismissed.asStateFlow()

    val serverAddress: StateFlow<ServerAddress> = store.server

    val installedVersionName: String get() = packageInfo()?.versionName ?: "?"
    private val installedVersionCode: Long get() = packageInfo()?.longVersionCode ?: 0L

    /** On start-up: at most once a day, quietly (a failure just shows nothing). */
    fun checkIfDue(today: LocalDate = LocalDate.now()) {
        if (!UpdateLogic.shouldAutoCheck(store.lastAutoCheck, today)) return
        store.lastAutoCheck = today
        check(quiet = true)
    }

    /** "Zoek naar update". */
    fun checkNow() = check(quiet = false)

    fun setServer(address: ServerAddress) {
        store.saveServer(address)
        _state.value = UpdateState.Idle
    }

    fun dismissCard() {
        _cardDismissed.value = true
    }

    private fun check(quiet: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!quiet) _state.value = UpdateState.Checking
            _state.value = try {
                val remote = server.fetchVersion(store.server.value)
                if (UpdateLogic.isNewer(installedVersionCode, remote)) {
                    _cardDismissed.value = false
                    UpdateState.Available(remote)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: UpdateException) {
                if (quiet) UpdateState.Idle else UpdateState.Failed(e.problem)
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed(UpdateProblem.CHECK_FAILED)
            }
        }
    }

    /** "Bijwerken": download, check the file, then it's Ready for Android's install screen. */
    fun download(remote: RemoteVersion) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(remote, 0f)
            val file = apkFile()
            _state.value = try {
                file.delete()
                val sha = server.download(store.server.value, remote, file) { p ->
                    _state.value = UpdateState.Downloading(remote, p)
                }
                val archive = context.packageManager.getPackageArchiveInfo(file.path, 0)
                val archiveVersion = archive?.longVersionCode
                when (val result = UpdateLogic.verify(remote, file.length(), sha, archive?.packageName, archiveVersion, context.packageName)) {
                    VerifyResult.OK -> UpdateState.Ready(remote)
                    else -> {
                        file.delete()
                        UpdateState.BadDownload(result)
                    }
                }
            } catch (e: UpdateException) {
                file.delete()
                UpdateState.Failed(e.problem)
            } catch (e: Exception) {
                file.delete()
                UpdateState.Failed(UpdateProblem.NOT_REACHABLE)
            }
        }
    }

    /** Android asks once whether Food diary may install apps; until then the install screen can't open. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Android's own install screen for the downloaded, checked APK. */
    fun installIntent(): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apkFile())
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun apkFile() = File(File(context.cacheDir, "updates"), "food-diary-update.apk")

    private fun packageInfo() = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()
}
