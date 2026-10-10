package nl.guido.foodtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flowOf
import nl.guido.foodtracker.core.data.repo.SyncEntry
import nl.guido.foodtracker.core.ui.FoodTheme
import nl.guido.foodtracker.core.ui.OpenScreen
import java.util.Optional
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var syncEntry: Optional<SyncEntry>

    /** A screen a notification asked to open (OpenScreen.EXTRA_ROUTE), until the navigation has opened it. */
    private var openRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRoute = intent.routeExtra()
        setContent {
            FoodTheme {
                FoodDiaryNavHost(
                    shouldOfferSignIn = syncEntry.map { it.shouldOfferSignIn }.orElse(flowOf(false)),
                    openRoute = openRoute,
                    onRouteOpened = { openRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.routeExtra()?.let { openRoute = it }
    }

    private fun Intent.routeExtra(): String? = getStringExtra(OpenScreen.EXTRA_ROUTE)
}
