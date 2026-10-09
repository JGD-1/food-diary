package nl.guido.foodtracker.feature.food.estimate

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.guido.foodtracker.core.model.Estimate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PrefsEstimateCache @Inject constructor(@ApplicationContext context: Context) : EstimateCache {
    private val prefs = context.getSharedPreferences("food_estimates", Context.MODE_PRIVATE)

    override fun get(key: String): Estimate? = prefs.getString(key, null)?.split(',')?.let { parts ->
        val n = parts.mapNotNull { it.toIntOrNull() }
        if (n.size == 3) Estimate(n[0], n[1], n[2]) else null
    }

    override fun put(key: String, estimate: Estimate) {
        prefs.edit().putString(key, "${estimate.low},${estimate.typical},${estimate.high}").apply()
    }
}
