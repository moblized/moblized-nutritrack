package com.moblized.nutritrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.moblized.nutritrack.data.Activity
import com.moblized.nutritrack.data.Goal
import com.moblized.nutritrack.data.MealieClient
import com.moblized.nutritrack.data.MealieException
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Profile
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.Sex
import com.moblized.nutritrack.data.Targets
import com.moblized.nutritrack.data.ThemeMode
import com.moblized.nutritrack.data.Units
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val CM_PER_IN = 2.54
private const val KG_PER_LB = 0.45359237

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onOpenTargets: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val repo = Graph.app.settings
    val p = settings.profile
    val us = settings.units == Units.US

    fun saveProfile(change: (Profile) -> Profile) {
        scope.launch { repo.updateProfile(change(repo.current().profile)) }
    }

    // Text fields keep their own state so typing isn't interrupted by saves.
    var age by remember { mutableStateOf(p.age.toString()) }
    val totalIn = p.heightCm / CM_PER_IN
    var feet by remember(us) { mutableStateOf((totalIn / 12).toInt().toString()) }
    var inches by remember(us) { mutableStateOf((totalIn % 12).roundToInt().toString()) }
    var cm by remember(us) { mutableStateOf(p.heightCm.roundToInt().toString()) }
    // Re-seeded when a scale weigh-in updates the profile.
    var weight by remember(us, settings.lastWeighInTime) {
        mutableStateOf(if (us) (p.weightKg / KG_PER_LB).roundToInt().toString() else fmtQty((p.weightKg * 10).roundToInt() / 10.0))
    }
    var kcalText by remember { mutableStateOf(settings.calorieOverride?.toString() ?: "") }
    var protein by remember { mutableIntStateOf(settings.proteinPct) }
    var carbs by remember { mutableIntStateOf(settings.carbPct) }
    var fat by remember { mutableIntStateOf(settings.fatPct) }

    var hcAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hcAvailable = Graph.app.health.isAvailable() }
    val weightLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        scope.launch {
            val ok = granted.containsAll(Graph.app.health.weightPermissions)
            repo.setWeightSync(ok)
            if (ok) {
                val w = Graph.app.syncWeight()
                snackbar.showSnackbar(
                    if (w != null) "Weight updated to ${fmtWeight(w.kg, us)} from your scale"
                    else "Connected. Your next weigh-in will update your weight.",
                )
            } else {
                snackbar.showSnackbar("Weight permission wasn't granted")
            }
        }
    }
    var mealieUrl by remember { mutableStateOf(settings.mealieUrl) }
    var mealieToken by remember { mutableStateOf(settings.mealieToken) }
    var mealieStatus by remember { mutableStateOf<String?>(null) }
    var mealieTesting by remember { mutableStateOf(false) }

    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        scope.launch {
            val ok = granted.containsAll(Graph.app.health.permissions)
            repo.setHealthConnect(ok)
            if (ok) {
                val n = Graph.app.syncRecent()
                snackbar.showSnackbar("Health Connect on · synced $n entries from the last 30 days")
            } else {
                snackbar.showSnackbar("Health Connect permission wasn't granted")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Section("Profile") }
            item {
                ChoiceRow(Sex.entries.toList(), p.sex, { it.label }, { s -> saveProfile { it.copy(sex = s) } })
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Age", age, Modifier.weight(1f)) { v ->
                        age = v
                        v.toIntOrNull()?.takeIf { it in 13..110 }?.let { a -> saveProfile { it.copy(age = a) } }
                    }
                    NumberField(if (us) "Weight (lb)" else "Weight (kg)", weight, Modifier.weight(1f)) { v ->
                        weight = v
                        parseNumber(v)?.takeIf { it > 20 }?.let { w ->
                            val kg = if (us) w * KG_PER_LB else w
                            saveProfile { it.copy(weightKg = kg) }
                        }
                    }
                }
            }
            item {
                if (us) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField("Height (ft)", feet, Modifier.weight(1f)) { v ->
                            feet = v
                            saveHeightUs(v, inches) { h -> saveProfile { it.copy(heightCm = h) } }
                        }
                        NumberField("(in)", inches, Modifier.weight(1f)) { v ->
                            inches = v
                            saveHeightUs(feet, v) { h -> saveProfile { it.copy(heightCm = h) } }
                        }
                    }
                } else {
                    NumberField("Height (cm)", cm, Modifier.fillMaxWidth()) { v ->
                        cm = v
                        parseNumber(v)?.takeIf { it in 100.0..250.0 }?.let { h -> saveProfile { it.copy(heightCm = h) } }
                    }
                }
            }
            item {
                Text("Activity", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Column {
                    Activity.entries.forEach { a ->
                        RadioLine(a.label, p.activity == a) { saveProfile { it.copy(activity = a) } }
                    }
                }
            }
            item {
                Text("Goal", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Column {
                    Goal.entries.forEach { g ->
                        RadioLine(g.label, p.goal == g) { saveProfile { it.copy(goal = g) } }
                    }
                }
            }

            item { Section("Daily targets") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${fmtKcal(Targets.calories(settings))} kcal per day",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (settings.calorieOverride != null) "Custom target"
                            else "Estimated from your profile (BMR ${fmtKcal(Targets.bmr(p))} kcal × activity, adjusted for your goal)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                NumberField("Custom calorie target (blank = automatic)", kcalText, Modifier.fillMaxWidth()) { v ->
                    kcalText = v
                    val k = v.toIntOrNull()
                    if (v.isBlank() || (k != null && k >= 800)) scope.launch { repo.setCalorieOverride(k) }
                }
            }
            item {
                Text("Macro split (% of calories)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Protein", protein.toString(), Modifier.weight(1f)) { v ->
                        protein = v.toIntOrNull()?.coerceIn(0, 100) ?: 0
                        if (protein + carbs + fat == 100) scope.launch { repo.setMacros(protein, carbs, fat) }
                    }
                    NumberField("Carbs", carbs.toString(), Modifier.weight(1f)) { v ->
                        carbs = v.toIntOrNull()?.coerceIn(0, 100) ?: 0
                        if (protein + carbs + fat == 100) scope.launch { repo.setMacros(protein, carbs, fat) }
                    }
                    NumberField("Fat", fat.toString(), Modifier.weight(1f)) { v ->
                        fat = v.toIntOrNull()?.coerceIn(0, 100) ?: 0
                        if (protein + carbs + fat == 100) scope.launch { repo.setMacros(protein, carbs, fat) }
                    }
                }
                val sum = protein + carbs + fat
                if (sum != 100) {
                    Text(
                        "Adds up to $sum% — needs to be 100% to save",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Vitamin, mineral & other targets") },
                    supportingContent = {
                        Text(
                            if (settings.overrides.isEmpty()) "Using recommended intakes for your age and sex"
                            else "${settings.overrides.size} customized",
                        )
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable(onClick = onOpenTargets),
                )
            }

            item { Section("Appearance") }
            item {
                ChoiceRow(ThemeMode.entries.toList(), settings.themeMode, { it.label }, { m -> scope.launch { repo.setTheme(m) } })
            }
            item {
                SwitchLine(
                    title = "Pure black dark mode",
                    subtitle = "True black backgrounds in dark mode",
                    checked = settings.pureBlack,
                ) { on -> scope.launch { repo.setPureBlack(on) } }
                Text(
                    "The app icon switches to its dark version whenever dark mode is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text("Units", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                ChoiceRow(Units.entries.toList(), settings.units, { it.label }, { u -> scope.launch { repo.setUnits(u) } })
            }

            item { Section("Health Connect") }
            item {
                if (hcAvailable) {
                    SwitchLine(
                        title = "Sync meals to Health Connect",
                        subtitle = "Samsung Health and other apps can show your calories, macros, vitamins and minerals",
                        checked = settings.healthConnect,
                    ) { on ->
                        if (on) {
                            hcLauncher.launch(Graph.app.health.permissions)
                        } else {
                            scope.launch { repo.setHealthConnect(false) }
                        }
                    }
                    if (settings.healthConnect) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val n = Graph.app.syncRecent()
                                    snackbar.showSnackbar("Synced $n entries")
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("Sync last 30 days now") }
                    }
                } else {
                    Text(
                        "Health Connect isn't available on this phone. On Android 13 and older, install " +
                            "\"Health Connect\" from the Play Store, then come back here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Section("Smart scale") }
            item {
                if (hcAvailable) {
                    SwitchLine(
                        title = "Update my weight from my scale",
                        subtitle = "Uses the latest weigh-in your scale app (like Libre Scale) saves to Health Connect, " +
                            "so calorie and protein targets follow your weight",
                        checked = settings.weightSync,
                    ) { on ->
                        if (on) {
                            weightLauncher.launch(Graph.app.health.weightPermissions)
                        } else {
                            scope.launch { repo.setWeightSync(false) }
                        }
                    }
                    if (settings.weightSync && settings.lastWeighInTime > 0) {
                        val whenText = java.time.Instant.ofEpochMilli(settings.lastWeighInTime)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        Text(
                            "Last weigh-in: ${fmtWeight(p.weightKg, us)} on ${dateLabel(whenText)}" +
                                (if (settings.lastWeighInSource.isNotBlank()) " from ${settings.lastWeighInSource}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    Text(
                        "Needs Health Connect, which isn't available on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Section("Mealie") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mealieUrl,
                        onValueChange = { mealieUrl = it; mealieStatus = null },
                        label = { Text("Server address") },
                        placeholder = { Text("http://192.168.1.10:9000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = mealieToken,
                        onValueChange = { mealieToken = it; mealieStatus = null },
                        label = { Text("API token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = mealieUrl.isNotBlank() && mealieToken.isNotBlank() && !mealieTesting,
                            onClick = {
                                mealieTesting = true
                                scope.launch {
                                    repo.setMealie(mealieUrl, mealieToken)
                                    mealieStatus = try {
                                        val who = MealieClient(mealieUrl, mealieToken).whoAmI()
                                        "Connected" + (if (who.isNotBlank()) " as $who" else "")
                                    } catch (e: MealieException) {
                                        e.message ?: "Couldn't connect"
                                    }
                                    mealieTesting = false
                                }
                            },
                        ) { Text(if (mealieTesting) "Testing…" else "Save & test") }
                        if (settings.mealieConfigured) {
                            TextButton(onClick = {
                                mealieUrl = ""
                                mealieToken = ""
                                mealieStatus = null
                                scope.launch { repo.setMealie("", "") }
                            }) { Text("Disconnect") }
                        }
                    }
                    mealieStatus?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Create a token in Mealie under your profile → API Tokens. Once connected, import recipes from " +
                            "My foods, and your Mealie meal plan shows on the diary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Section("About") }
            item {
                val count = remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    count.intValue = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Graph.app.usda.foodCount() }
                }
                Text(
                    "Food data: USDA FoodData Central (Foundation, SR Legacy and FNDDS), " +
                        "${String.format(java.util.Locale.US, "%,d", count.intValue)} foods stored on your phone. " +
                        "Barcode data: Open Food Facts (ODbL). Targets: US Dietary Reference Intakes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun fmtWeight(kg: Double, us: Boolean): String =
    if (us) "${fmtQty((kg / KG_PER_LB * 10).roundToInt() / 10.0)} lb" else "${fmtQty((kg * 10).roundToInt() / 10.0)} kg"

private fun saveHeightUs(feet: String, inches: String, onValid: (Double) -> Unit) {
    val f = feet.toIntOrNull() ?: return
    val i = parseNumber(inches.ifBlank { "0" }) ?: return
    val totalIn = f * 12 + i
    if (totalIn in 40.0..100.0) onValid(totalIn * CM_PER_IN)
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
