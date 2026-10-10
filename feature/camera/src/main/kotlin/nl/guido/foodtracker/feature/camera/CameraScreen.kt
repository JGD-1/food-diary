package nl.guido.foodtracker.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nl.guido.foodtracker.feature.camera.vision.BarcodeReader
import nl.guido.foodtracker.feature.camera.vision.LabelReader
import nl.guido.foodtracker.feature.camera.vision.ScaleReader
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private enum class CameraUse { BARCODE, LABEL, SCALE }

private val CameraStep.cameraUse: CameraUse?
    get() = when (this) {
        CameraStep.ScanBarcode -> CameraUse.BARCODE
        is CameraStep.PhotoLabel, is CameraStep.ReadingLabel -> CameraUse.LABEL
        is CameraStep.ReadScale -> if (typing) null else CameraUse.SCALE
        else -> null
    }

/** The one Scan screen: barcodes, nutrition labels, the kitchen scale, and typing as the fallback. */
@Composable
internal fun CameraScreen(
    onFinished: (CameraOutcome) -> Unit,
    onLeave: () -> Unit,
    onSearchByName: () -> Unit,
    vm: CameraViewModel = hiltViewModel(),
) {
    val step by vm.step.collectAsStateWithLifecycle()
    val scale by vm.scale.collectAsStateWithLifecycle()
    val outcome by vm.outcome.collectAsStateWithLifecycle()
    LaunchedEffect(outcome) { outcome?.let(onFinished) }
    BackHandler { if (!vm.back()) onLeave() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) askPermission.launch(Manifest.permission.CAMERA) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            // Sharper frames than the default make small digits easier to read.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
        }
    }
    val analysisThread = remember { Executors.newSingleThreadExecutor() }
    val barcodeReader = remember { BarcodeReader(vm::onBarcodeSeen) }
    val scaleReader = remember { ScaleReader(vm::onScaleFrame) }
    val labelReader = remember { LabelReader() }
    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            barcodeReader.close()
            labelReader.close()
            analysisThread.shutdown()
        }
    }

    val use = step.cameraUse.takeIf { hasPermission }
    var lightOn by remember { mutableStateOf(false) }
    LaunchedEffect(use) {
        controller.clearImageAnalysisAnalyzer()
        when (use) {
            null -> { controller.unbind(); return@LaunchedEffect }
            CameraUse.LABEL -> controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            CameraUse.BARCODE -> {
                controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                controller.setImageAnalysisAnalyzer(analysisThread, barcodeReader)
            }
            CameraUse.SCALE -> {
                controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                controller.setImageAnalysisAnalyzer(analysisThread, scaleReader)
            }
        }
        controller.bindToLifecycle(lifecycleOwner)
        controller.enableTorch(lightOn)
    }
    LaunchedEffect(lightOn) { if (use != null) controller.enableTorch(lightOn) }

    val takeLabelPhoto = {
        vm.onLabelPhotoTaken()
        controller.takePicture(
            context.mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = labelReader.read(image, vm::onLabelRead)
                override fun onError(exception: ImageCaptureException) = vm.onLabelRead(emptyList())
            },
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { if (!vm.back()) onLeave() }) { Text(stringResource(R.string.camera_back)) }
            Text(
                stringResource(R.string.camera_camera_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (use != null) {
                TextButton(onClick = { lightOn = !lightOn }) {
                    Text(stringResource(if (lightOn) R.string.camera_light_off else R.string.camera_light_on))
                }
            }
        }

        if (step.cameraUse != null) {
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                if (hasPermission) {
                    AndroidView(
                        factory = { PreviewView(it).apply { this.controller = controller } },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // A frame to aim with.
                    Box(
                        Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(if (use == CameraUse.LABEL) 300.dp else 140.dp)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp)),
                    )
                    if (step is CameraStep.ReadingLabel) CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    PermissionPanel(onAllow = { askPermission.launch(Manifest.permission.CAMERA) })
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth().then(
                if (step.cameraUse != null) Modifier.heightIn(max = 420.dp).padding(top = 12.dp) else Modifier.weight(1f),
            ),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val s = step) {
                    CameraStep.ScanBarcode -> BarcodePanel(vm, onSearchByName)
                    is CameraStep.TypeBarcode -> TypeBarcodePanel(s, vm)
                    is CameraStep.LookingUp -> LookingUpPanel(s.code)
                    is CameraStep.NotFound -> NotFoundPanel(s, vm, onSearchByName)
                    is CameraStep.PhotoLabel -> LabelPanel(onTake = takeLabelPhoto, vm, enabled = hasPermission)
                    is CameraStep.ReadingLabel -> Body(stringResource(R.string.camera_label_reading))
                    is CameraStep.EditFood -> EditFoodPanel(s, vm)
                    is CameraStep.HowMuch -> HowMuchPanel(s, vm)
                    is CameraStep.ReadScale -> ScalePanel(s, scale, vm)
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun MainButton(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(text) }

@Composable
private fun OtherButton(text: String, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(text) }

@Composable
private fun PermissionPanel(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Body(stringResource(R.string.camera_permission_body))
        MainButton(stringResource(R.string.camera_permission_allow), onClick = onAllow)
    }
}

@Composable
private fun BarcodePanel(vm: CameraViewModel, onSearchByName: () -> Unit) {
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(10_000); showHint = true }
    Title(stringResource(R.string.camera_barcode_title))
    Body(stringResource(if (showHint) R.string.camera_barcode_hint else R.string.camera_barcode_body))
    OtherButton(stringResource(R.string.camera_barcode_type), vm::typeBarcode)
    OtherButton(stringResource(R.string.camera_alt_search), onSearchByName)
    OtherButton(stringResource(R.string.camera_alt_label), vm::scanLabel)
}

@Composable
private fun TypeBarcodePanel(s: CameraStep.TypeBarcode, vm: CameraViewModel) {
    Title(stringResource(R.string.camera_barcode_type))
    NumberField(s.text, vm::onTypedBarcode, stringResource(R.string.camera_barcode_type_field), KeyboardType.Number)
    if (s.invalid) Body(stringResource(R.string.camera_barcode_type_invalid))
    MainButton(stringResource(R.string.camera_barcode_type_go), enabled = s.text.length >= 8, onClick = vm::submitTypedBarcode)
    OtherButton(stringResource(R.string.camera_scan_again), vm::scanAgain)
}

@Composable
private fun LookingUpPanel(code: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Body(stringResource(R.string.camera_looking_up, code))
    }
}

@Composable
private fun NotFoundPanel(s: CameraStep.NotFound, vm: CameraViewModel, onSearchByName: () -> Unit) {
    Title(stringResource(if (s.offline) R.string.camera_offline_title else R.string.camera_not_found_title))
    Body(stringResource(if (s.offline) R.string.camera_offline_body else R.string.camera_not_found_body))
    MainButton(stringResource(R.string.camera_alt_label), onClick = vm::scanLabel)
    OtherButton(stringResource(R.string.camera_alt_search), onSearchByName)
    OtherButton(stringResource(R.string.camera_alt_type_food), vm::typeFood)
    OtherButton(stringResource(R.string.camera_scan_again), vm::scanAgain)
}

@Composable
private fun LabelPanel(onTake: () -> Unit, vm: CameraViewModel, enabled: Boolean) {
    Title(stringResource(R.string.camera_label_title))
    Body(stringResource(R.string.camera_label_body))
    MainButton(stringResource(R.string.camera_label_take), enabled = enabled, onClick = onTake)
    OtherButton(stringResource(R.string.camera_alt_type_food), vm::typeFood)
}

@Composable
private fun EditFoodPanel(s: CameraStep.EditFood, vm: CameraViewModel) {
    val d = s.draft
    Title(stringResource(R.string.camera_food_title))
    Body(
        when (s.readCount) {
            null -> stringResource(R.string.camera_food_typed)
            0 -> stringResource(R.string.camera_food_unread)
            else -> stringResource(R.string.camera_food_read, s.readCount ?: 0)
        },
    )
    if (s.readCount != null) OtherButton(stringResource(R.string.camera_food_retake), vm::scanLabel)
    FoodFields(d, vm::onDraftChange)
    MainButton(stringResource(R.string.camera_food_save), enabled = d.canSave, onClick = vm::saveFood)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HowMuchPanel(s: CameraStep.HowMuch, vm: CameraViewModel) {
    val food = s.food
    Title(stringResource(R.string.camera_amount_title))
    Text(listOfNotNull(food.name, food.brand).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
    Body(
        stringResource(
            if (food.isDrink) R.string.camera_amount_per100ml else R.string.camera_amount_per100g,
            food.per100g.kcal.roundToInt(),
        ),
    )
    val chips = amountChips(food, s.pieces)
    if (chips.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { chip ->
                FilterChip(
                    selected = parseAmount(s.grams) == chip.grams,
                    onClick = { vm.onGramsChange(formatGrams(chip.grams)) },
                    label = { Text(chipText(chip, food.isDrink)) },
                )
            }
        }
    }
    NumberField(
        s.grams, vm::onGramsChange,
        stringResource(if (food.isDrink) R.string.camera_amount_ml else R.string.camera_amount_grams),
    )
    parseAmount(s.grams)?.takeIf { it > 0 }?.let { grams ->
        Body(stringResource(R.string.camera_amount_total, food.per100g.forGrams(grams).kcal.roundToInt()))
    }
    MainButton(stringResource(R.string.camera_amount_done), enabled = (parseAmount(s.grams) ?: 0.0) > 0, onClick = vm::finishWithFood)
    OtherButton(stringResource(R.string.camera_amount_read_scale)) { vm.readScaleFor(food) }
}

/** "1 serving · 45 g", "Whole pack · 750 g", "1 apple ≈ 150 g". */
@Composable
private fun chipText(chip: AmountChip, drink: Boolean): String {
    val amount = stringResource(if (drink) R.string.camera_chip_ml else R.string.camera_chip_grams, formatGrams(chip.grams))
    return when (chip.kind) {
        AmountChip.Kind.SERVING -> stringResource(R.string.camera_chip_serving, amount)
        AmountChip.Kind.PACK -> stringResource(R.string.camera_chip_pack, amount)
        AmountChip.Kind.PIECE -> stringResource(R.string.camera_chip_piece, chip.label.orEmpty(), amount)
    }
}

@Composable
private fun ScalePanel(s: CameraStep.ReadScale, scale: ScaleView, vm: CameraViewModel) {
    if (s.typing) {
        Title(stringResource(R.string.camera_alt_type_grams))
        NumberField(s.typed, vm::onTypedGrams, stringResource(R.string.camera_scale_type_field))
        MainButton(stringResource(R.string.camera_scale_type_use), enabled = (parseAmount(s.typed) ?: 0.0) > 0, onClick = vm::useTypedGrams)
        return
    }
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(scale.grams) { showHint = false; if (scale.grams == null) { delay(8_000); showHint = true } }
    Title(stringResource(R.string.camera_scale_title))
    val grams = scale.grams
    when {
        grams == null -> Body(stringResource(if (showHint) R.string.camera_scale_hint else R.string.camera_scale_body))
        grams == 0.0 -> Body(stringResource(R.string.camera_scale_zero))
        else -> {
            Text(
                stringResource(R.string.camera_scale_reading, formatGrams(grams)),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            MainButton(stringResource(R.string.camera_scale_use, formatGrams(grams))) { vm.useGrams(grams) }
        }
    }
    OtherButton(stringResource(R.string.camera_alt_type_grams), vm::typeGrams)
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType = KeyboardType.Decimal) =
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
