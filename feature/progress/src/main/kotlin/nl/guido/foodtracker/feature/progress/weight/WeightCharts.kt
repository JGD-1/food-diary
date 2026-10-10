package nl.guido.foodtracker.feature.progress.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.guido.foodtracker.core.ui.FoodTheme
import nl.guido.foodtracker.feature.progress.Format
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.max

/**
 * "Over time": one dot per weekly weigh-in joined by a line, the goal as a dashed line,
 * month names underneath and the latest weight next to the last dot.
 */
@Composable
internal fun WeightLineChart(
    points: List<WeekPoint>,
    targetKg: Double,
    goalLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 11.sp, color = secondary)
    val valueStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(168.dp)
            .semantics { contentDescription = description },
    ) {
        val edge = 8.dp.toPx()
        val top = 8.dp.toPx()
        val monthRow = measurer.measure("Jul", labelStyle).size.height + 6.dp.toPx()
        val plotBottom = size.height - monthRow
        val kgs = points.map { it.kg } + targetKg
        val low = kgs.min() - 0.5
        val high = kgs.max() + 0.5
        fun y(kg: Double) = (top + (high - kg) / (high - low) * (plotBottom - top)).toFloat()
        fun x(i: Int) = if (points.size <= 1) size.width / 2 else edge + i * (size.width - 2 * edge) / (points.size - 1)

        // Goal line, with its label just above it (or below when there's no room above).
        val goalY = y(targetKg)
        drawLine(
            color = secondary,
            start = Offset(edge, goalY),
            end = Offset(size.width - edge, goalY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        val goal = measurer.measure(goalLabel, labelStyle)
        val goalTextY = if (goalY - goal.size.height - 2.dp.toPx() >= 0) goalY - goal.size.height - 2.dp.toPx() else goalY + 2.dp.toPx()
        drawText(goal, topLeft = Offset(edge, goalTextY))

        if (points.isEmpty()) return@Canvas

        // The line and its dots; the latest dot is a little bigger.
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.kg)) else path.lineTo(x(i), y(p.kg)) }
        drawPath(path, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEachIndexed { i, p ->
            val r = if (i == points.lastIndex) 5.dp.toPx() else 3.5.dp.toPx()
            drawCircle(accent, radius = r, center = Offset(x(i), y(p.kg)))
        }

        // Latest weight, below the last dot (above it if that's where the room is).
        val last = points.last()
        val value = measurer.measure(Format.kg(last.kg), valueStyle)
        val lastX = x(points.lastIndex)
        val lastY = y(last.kg)
        val valueX = (lastX - value.size.width).coerceIn(0f, size.width - value.size.width)
        val below = lastY + 8.dp.toPx()
        val valueY = if (below + value.size.height <= plotBottom) below else lastY - 8.dp.toPx() - value.size.height
        drawText(value, topLeft = Offset(valueX, valueY))

        // Month names where a new month starts, skipping any that would overlap.
        var lastLabelEnd = -1f
        var previousMonth: YearMonth? = null
        points.forEachIndexed { i, p ->
            val month = YearMonth.from(p.date)
            if (month == previousMonth) return@forEachIndexed
            previousMonth = month
            val label = measurer.measure(Format.monthShort(month), labelStyle)
            val left = (x(i) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            if (left > lastLabelEnd + 4.dp.toPx()) {
                drawText(label, topLeft = Offset(left, size.height - label.size.height))
                lastLabelEnd = left + label.size.width
            }
        }
    }
}

/**
 * "Week to week": a bar per weekly change around a middle line. Changes toward the target
 * are in the accent colour, the others in the soft green; neither is a warning.
 */
@Composable
internal fun WeekChangeBars(
    changes: List<WeekChange>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = FoodTheme.colors.accentTrack
    val away = FoodTheme.colors.accentOutline
    Canvas(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .semantics { contentDescription = description },
    ) {
        val mid = size.height / 2
        drawLine(track, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        if (changes.isEmpty()) return@Canvas

        val slots = max(changes.size, CHANGE_BARS)
        val slot = size.width / slots
        val barWidth = minOf(18.dp.toPx(), slot * 0.7f)
        val reach = mid - 4.dp.toPx()
        val biggest = max(changes.maxOf { abs(it.deltaKg) }, 0.5)
        val radius = CornerRadius(3.dp.toPx())
        // Right-aligned, so the latest week is always at the right edge.
        val firstSlot = slots - changes.size
        changes.forEachIndexed { i, change ->
            val h = max((abs(change.deltaKg) / biggest * reach).toFloat(), 2.dp.toPx())
            val left = (firstSlot + i) * slot + (slot - barWidth) / 2
            val topY = if (change.deltaKg > 0) mid - h else mid
            drawRoundRect(
                color = if (change.towardTarget) accent else away,
                topLeft = Offset(left, topY),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }
    }
}
