package nl.guido.foodtracker.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Tab icons that match their labels (Round 17, P7): a scale for Gewicht, bars for Overzicht, a runner for Sport.
 * Drawn here (24 × 24) so the app doesn't need the large extended icon library.
 */
object FoodIcons {
    /** A bathroom scale: rounded body with a dial window and a needle. */
    val Scale: ImageVector by lazy {
        icon(
            "Scale",
            "M6,3h12c1.66,0 3,1.34 3,3v12c0,1.66 -1.34,3 -3,3H6c-1.66,0 -3,-1.34 -3,-3V6c0,-1.66 1.34,-3 3,-3z" +
                "M7.5,6.5c-0.55,0 -1,0.45 -1,1v2c0,0.55 0.45,1 1,1h9c0.55,0 1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1z",
            "M11.25,7.5h1.5v2.5h-1.5z",
        )
    }

    /** Three bars of different heights. */
    val Bars: ImageVector by lazy {
        icon("Bars", "M5,9.2h3V19H5V9.2zM10.6,5h2.8v14h-2.8V5zM16.2,13H19v6h-2.8V13z")
    }

    /** A running figure. */
    val Run: ImageVector by lazy {
        icon(
            "Run",
            "M13.49,5.48c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z" +
                "M9.89,19.38l1,-4.4 2.1,2v6h2v-7.5l-2.1,-2 0.6,-3c1.3,1.5 3.3,2.5 5.5,2.5v-2c-1.9,0 -3.5,-1 -4.3,-2.4" +
                "l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1 -0.3,0 -0.5,0.1 -0.8,0.1l-5.2,2.2v4.7h2v-3.4l1.8,-0.7 -1.6,8.1 -4.9,-1 -0.4,2 7,1.4z",
        )
    }

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(pathData = addPathNodes(data), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
                }
            }
            .build()
}
