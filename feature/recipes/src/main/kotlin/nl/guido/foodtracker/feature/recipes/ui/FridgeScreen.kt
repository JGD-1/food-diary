package nl.guido.foodtracker.feature.recipes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.StoragePlace
import nl.guido.foodtracker.core.ui.CardShadow
import nl.guido.foodtracker.core.ui.FoodTheme
import nl.guido.foodtracker.feature.recipes.FridgeViewModel
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.WaitingPlatesViewModel
import nl.guido.foodtracker.feature.recipes.logic.StoredBox
import nl.guido.foodtracker.feature.recipes.logic.formatGrams
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val cookedDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/**
 * "In the fridge" (round 6, D3): every box not eaten yet, with what is left in it. "Weigh a portion"
 * takes some out (the box updates itself); empty boxes leave the list. The freezer is listed under it.
 */
@Composable
internal fun FridgeScreen(
    onBack: () -> Unit,
    onWeigh: (batchId: Id, boxId: Id) -> Unit,
    vm: FridgeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { RecipesTopBar(stringResource(R.string.recipes_fridge_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) {
            Loading()
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Hint(stringResource(R.string.recipes_fridge_shared)) }
            if (state.fridge.isEmpty()) {
                item { Hint(stringResource(R.string.recipes_fridge_empty)) }
            }
            items(state.fridge, key = { "box-" + it.container.id }) { box ->
                BoxCard(
                    box,
                    onWeigh = { onWeigh(box.batch.id, box.container.id) },
                    moveLabel = stringResource(R.string.recipes_fridge_to_freezer),
                    onMove = { vm.move(box.container, StoragePlace.FREEZER) },
                )
            }
            if (state.freezer.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.recipes_freezer_section)) }
                items(state.freezer, key = { "box-" + it.container.id }) { box ->
                    BoxCard(
                        box,
                        onWeigh = { onWeigh(box.batch.id, box.container.id) },
                        moveLabel = stringResource(R.string.recipes_freezer_take_out),
                        onMove = { vm.move(box.container, StoragePlace.FRIDGE) },
                    )
                }
            }
            item { Hint(stringResource(R.string.recipes_fridge_empty_leaves)) }
        }
    }
}

@Composable
private fun BoxCard(box: StoredBox, onWeigh: () -> Unit, moveLabel: String, onMove: () -> Unit) {
    val kcal = box.batch.shareFor(box.gramsLeft).kcal.roundToInt()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CardShadow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.recipes_box_name, box.batch.name, box.container.number),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Hint(stringResource(R.string.recipes_box_cooked, box.batch.cookedOn.format(cookedDay)))
            }
            Hint(
                if (box.gramsLeft < box.container.grams) {
                    stringResource(R.string.recipes_box_left_of, formatGrams(box.gramsLeft), formatGrams(box.container.grams), kcal)
                } else {
                    stringResource(R.string.recipes_box_left, formatGrams(box.gramsLeft), kcal)
                },
            )
            LinearProgressIndicator(
                progress = { (box.gramsLeft / box.container.grams).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onWeigh, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.recipes_box_weigh)) }
                TextButton(onClick = onMove) { Text(moveLabel) }
            }
        }
    }
}

/** The fridge button with how many boxes are in it (D3), for the top of Recipes. */
@Composable
internal fun FridgeButton(count: Int, onClick: () -> Unit) {
    val label = stringResource(R.string.recipes_fridge_button, count)
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(FridgeIcon, contentDescription = label)
        }
    }
}

/** "Guido dished up for you" (D2): add it to my diary, or "Not mine" (it goes in the fridge). */
@Composable
internal fun WaitingPlateCard(plate: WaitingPlatesViewModel.Plate, onAdd: () -> Unit, onNotMine: () -> Unit) {
    val lilac = FoodTheme.colors.tints.lilac
    val kcal = plate.batch.shareFor(plate.portion.grams).kcal.roundToInt()
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = lilac.fill)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                plate.servedByName?.let { stringResource(R.string.recipes_waiting_from, it) }
                    ?: stringResource(R.string.recipes_waiting_from_someone),
                style = MaterialTheme.typography.labelLarge,
                color = lilac.ink,
            )
            Text(plate.batch.name, style = MaterialTheme.typography.titleMedium)
            Hint(stringResource(R.string.recipes_waiting_detail, formatGrams(plate.portion.grams), kcal))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.recipes_waiting_add)) }
                TextButton(onClick = onNotMine) { Text(stringResource(R.string.recipes_waiting_not_mine)) }
            }
        }
    }
}

/** A fridge with its door line and handles (from the design). */
internal val FridgeIcon: ImageVector = ImageVector.Builder("Fridge", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        pathData = addPathNodes("M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM6 10h12M9 5v2M9 13v3"),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()
