package nl.guido.foodtracker.feature.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import nl.guido.foodtracker.feature.sync.local.SyncPrefs
import javax.inject.Inject
import javax.inject.Singleton

/** For the app shell: whether to open the sign-in screen on start (until signed in or "Not now"). */
@Singleton
class SyncEntry @Inject constructor(manager: SyncManager, private val prefs: SyncPrefs) {
    private val dismissed = MutableStateFlow(prefs.signInOfferDismissed)

    val shouldOfferSignIn: Flow<Boolean> =
        combine(manager.state, dismissed) { state, dismissed -> state.configured && !state.signedIn && !dismissed }

    fun dismissSignInOffer() {
        prefs.signInOfferDismissed = true
        dismissed.value = true
    }
}
