package nl.guido.foodtracker.feature.sport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.guido.foodtracker.core.ui.FoodTheme

/**
 * Health Connect opens this when someone taps "why does this app need access" (required by Health
 * Connect). Says in plain words what is read and that nothing leaves the phone except the own backup.
 */
class SportPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoodTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.sport_privacy_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.sport_privacy_body), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.sport_privacy_close)) }
                    }
                }
            }
        }
    }
}
