package nl.guido.foodtracker.feature.recipes.note

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nl.guido.foodtracker.core.ui.FoodPickPurpose
import nl.guido.foodtracker.core.ui.amountText
import nl.guido.foodtracker.feature.recipes.R
import nl.guido.foodtracker.feature.recipes.ui.Hint
import nl.guido.foodtracker.feature.recipes.ui.Loading
import nl.guido.foodtracker.feature.recipes.ui.PickedFoods
import nl.guido.foodtracker.feature.recipes.ui.RecipesTopBar
import nl.guido.foodtracker.feature.recipes.ui.SectionTitle
import nl.guido.foodtracker.feature.recipes.ui.openFoodPick
import java.util.concurrent.Executors

/**
 * "Scan a note" (round 4, H): a live camera like the barcode scanner. Hold the phone over a
 * handwritten ingredient list; lines appear as they are read. Then check each line (its food and
 * amount) and add the ticked ones. [onDone] gets the result for the screen that opened it.
 */
@Composable
internal fun NoteScanScreen(
    navController: NavController,
    savedState: SavedStateHandle,
    purpose: FoodPickPurpose,
    onDone: (String) -> Unit,
    vm: NoteScanViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    PickedFoods(savedState, vm::onPicked)
    val leave = {
        if (state.phase == NoteScanViewModel.Phase.CHECKING) vm.scanMore() else navController.popBackStack()
    }
    BackHandler { leave() }

    Scaffold(
        topBar = { RecipesTopBar(stringResource(R.string.recipes_note_title), onBack = { leave() }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                NoteScanViewModel.Phase.SCANNING -> Scanning(state, vm)
                NoteScanViewModel.Phase.MATCHING -> {
                    Hint(stringResource(R.string.recipes_note_matching), Modifier.padding(16.dp))
                    Loading()
                }
                NoteScanViewModel.Phase.CHECKING -> Checking(
                    state, vm,
                    onChange = { key ->
                        vm.startChange(key)
                        openFoodPick(navController, purpose)
                    },
                    onAdd = { onDone(vm.result()) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Scanning(state: NoteScanViewModel.State, vm: NoteScanViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) askPermission.launch(Manifest.permission.CAMERA) }
    var lightOn by remember { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            // Sharp frames: handwriting is read better at a higher resolution.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
        }
    }
    val analysisThread = remember { Executors.newSingleThreadExecutor() }
    val reader = remember { NoteTextReader(vm::onFrame) }
    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            reader.close()
            analysisThread.shutdown()
        }
    }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(analysisThread, reader)
        controller.bindToLifecycle(lifecycleOwner)
        controller.enableTorch(lightOn)
    }
    LaunchedEffect(lightOn) { if (hasPermission) controller.enableTorch(lightOn) }

    Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))) {
        if (hasPermission) {
            AndroidView(factory = { PreviewView(it).apply { this.controller = controller } }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.8f)
                    .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp)),
            )
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint(stringResource(R.string.recipes_note_permission))
                Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.recipes_note_allow_camera))
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(top = 12.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Hint(stringResource(if (state.lines.isEmpty()) R.string.recipes_note_hold_still else R.string.recipes_note_keep_holding))
            state.lines.forEach { line ->
                Text(line.text, style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                onClick = vm::check,
                enabled = state.lines.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(pluralStringResource(R.plurals.recipes_note_check, state.lines.size, state.lines.size))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasPermission) {
                    OutlinedButton(onClick = { lightOn = !lightOn }) {
                        Text(stringResource(if (lightOn) R.string.recipes_note_light_off else R.string.recipes_note_light_on))
                    }
                }
                if (state.lines.isNotEmpty()) {
                    TextButton(onClick = vm::startAgain) { Text(stringResource(R.string.recipes_note_start_again)) }
                }
            }
        }
    }
}

@Composable
private fun Checking(state: NoteScanViewModel.State, vm: NoteScanViewModel, onChange: (String) -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(stringResource(R.string.recipes_note_check_title))
        Hint(stringResource(R.string.recipes_note_check_hint))
        state.items.forEach { item ->
            CheckRow(item, onToggle = { vm.toggle(item.key) }, onChange = { onChange(item.key) })
            HorizontalDivider()
        }
        val count = state.chosen.size
        Button(onClick = onAdd, enabled = count > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(pluralStringResource(R.plurals.recipes_note_add, count, count))
        }
        TextButton(onClick = vm::scanMore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.recipes_note_scan_more))
        }
    }
}

@Composable
private fun CheckRow(item: NoteScanViewModel.Item, onToggle: () -> Unit, onChange: () -> Unit) {
    val food = item.food
    Row(
        Modifier.fillMaxWidth().then(
            if (food != null) Modifier.toggleable(item.selected, role = Role.Checkbox, onValueChange = { onToggle() }) else Modifier,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.selected, onCheckedChange = null, enabled = food != null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(item.title ?: stringResource(R.string.recipes_note_no_food), style = MaterialTheme.typography.bodyLarge)
            Hint(stringResource(R.string.recipes_note_read, item.read))
            val grams = item.grams
            if (food != null) {
                Hint(
                    if (grams != null) amountText(item.label, grams, food.isDrink)
                    else stringResource(R.string.recipes_note_amount_later),
                )
            }
        }
        TextButton(onClick = onChange) {
            Text(stringResource(if (food == null) R.string.recipes_note_choose_food else R.string.recipes_note_change_food))
        }
    }
}
