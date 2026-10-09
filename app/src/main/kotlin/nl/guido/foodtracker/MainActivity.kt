package nl.guido.foodtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.flowOf
import nl.guido.foodtracker.core.data.repo.SyncEntry
import nl.guido.foodtracker.core.ui.FoodTheme
import java.util.Optional
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var syncEntry: Optional<SyncEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodTheme {
                FoodDiaryNavHost(
                    shouldOfferSignIn = syncEntry.map { it.shouldOfferSignIn }.orElse(flowOf(false)),
                )
            }
        }
    }
}
