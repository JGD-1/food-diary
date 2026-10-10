package nl.guido.foodtracker.feature.camera

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType

/** The food's fields: name, brand, per 100 g values (fibre, sugar and salt optional) and "drink". */
@Composable
internal fun FoodFields(d: FoodDraft, onChange: (FoodDraft) -> Unit) {
    FormText(d.name, { onChange(d.copy(name = it)) }, stringResource(R.string.camera_food_name))
    FormText(d.brand, { onChange(d.copy(brand = it)) }, stringResource(R.string.camera_food_brand))
    Text(
        stringResource(if (d.isDrink) R.string.camera_food_per100ml else R.string.camera_food_per100g),
        style = MaterialTheme.typography.titleMedium,
    )
    FormNumber(d.kcal, { onChange(d.copy(kcal = it)) }, stringResource(R.string.camera_food_kcal))
    FormNumber(d.protein, { onChange(d.copy(protein = it)) }, stringResource(R.string.camera_food_protein))
    FormNumber(d.carbs, { onChange(d.copy(carbs = it)) }, stringResource(R.string.camera_food_carbs))
    FormNumber(d.fat, { onChange(d.copy(fat = it)) }, stringResource(R.string.camera_food_fat))
    Text(stringResource(R.string.camera_food_optional), style = MaterialTheme.typography.titleMedium)
    FormNumber(d.fibre, { onChange(d.copy(fibre = it)) }, stringResource(R.string.camera_food_fibre))
    FormNumber(d.sugar, { onChange(d.copy(sugar = it)) }, stringResource(R.string.camera_food_sugar))
    FormNumber(d.salt, { onChange(d.copy(salt = it)) }, stringResource(R.string.camera_food_salt))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.camera_food_is_drink), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = d.isDrink, onCheckedChange = { onChange(d.copy(isDrink = it)) })
    }
}

@Composable
private fun FormText(value: String, onChange: (String) -> Unit, label: String) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())

@Composable
private fun FormNumber(value: String, onChange: (String) -> Unit, label: String) =
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
