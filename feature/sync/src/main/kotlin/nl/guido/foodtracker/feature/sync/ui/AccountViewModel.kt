package nl.guido.foodtracker.feature.sync.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.guido.foodtracker.feature.sync.AccountState
import nl.guido.foodtracker.feature.sync.SyncEntry
import nl.guido.foodtracker.feature.sync.SyncManager
import nl.guido.foodtracker.feature.sync.export.Exporter
import javax.inject.Inject

enum class ExportResult { SAVED, FAILED }

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val manager: SyncManager,
    private val exporter: Exporter,
    private val entry: SyncEntry,
) : ViewModel() {
    val state: StateFlow<AccountState> = manager.state

    private val _export = MutableStateFlow<ExportResult?>(null)
    val export: StateFlow<ExportResult?> = _export.asStateFlow()

    private val _joining = MutableStateFlow(false)
    val joining: StateFlow<Boolean> = _joining.asStateFlow()

    fun signInUrl(): String = manager.beginSignIn()

    fun notNow() = entry.dismissSignInOffer()

    fun syncNow() {
        viewModelScope.launch { manager.syncNow() }
    }

    fun join(code: String, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            _joining.value = true
            val ok = manager.joinHousehold(code)
            _joining.value = false
            done(ok)
        }
    }

    fun exportFileName() = exporter.suggestedFileName()

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _export.value = if (exporter.exportTo(uri)) ExportResult.SAVED else ExportResult.FAILED
        }
    }
}
