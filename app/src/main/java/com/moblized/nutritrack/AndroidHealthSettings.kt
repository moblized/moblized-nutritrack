package com.moblized.nutritrack

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.HealthSync
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.Units
import com.moblized.nutritrack.data.localDate
import com.moblized.nutritrack.ui.Section
import com.moblized.nutritrack.ui.SwitchLine
import com.moblized.nutritrack.ui.dateLabel
import com.moblized.nutritrack.ui.fmtWeight
import kotlinx.coroutines.launch

/** Settings for Health Connect: syncing meals out, and weigh-ins from a smart scale app in. */
@Composable
fun AndroidHealthSettings(health: HealthSync, settings: Settings, showMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val repo = Graph.app.settings
    val us = settings.units == Units.US
    var hcAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hcAvailable = health.available() }

    val weightLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        scope.launch {
            val ok = granted.containsAll(health.weightPermissions)
            repo.setWeightSync(ok)
            if (ok) {
                val w = Graph.app.syncWeight()
                showMessage(
                    if (w != null) "Weight updated to ${fmtWeight(w.kg, us)} from your scale"
                    else "Connected. Your next weigh-in will update your weight.",
                )
            } else {
                showMessage("Weight permission wasn't granted")
            }
        }
    }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        scope.launch {
            val ok = granted.containsAll(health.permissions)
            repo.setHealthConnect(ok)
            if (ok) {
                val n = Graph.app.syncRecent()
                showMessage("Health Connect on · synced $n entries from the last 30 days")
            } else {
                showMessage("Health Connect permission wasn't granted")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("❤️ Health Connect")
        if (hcAvailable) {
            Column {
                SwitchLine(
                    title = "Sync meals to Health Connect",
                    subtitle = "Samsung Health and other apps can show your calories, macros, vitamins and minerals",
                    checked = settings.healthConnect,
                ) { on ->
                    if (on) {
                        hcLauncher.launch(health.permissions)
                    } else {
                        scope.launch { repo.setHealthConnect(false) }
                    }
                }
                if (settings.healthConnect) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val n = Graph.app.syncRecent()
                                showMessage("Synced $n entries")
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Sync last 30 days now") }
                }
            }
        } else {
            Text(
                "Health Connect isn't available on this phone. On Android 13 and older, install " +
                    "\"Health Connect\" from the Play Store, then come back here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("⚖️ Smart scale")
        if (hcAvailable) {
            Column {
                SwitchLine(
                    title = "Update my weight from my scale",
                    subtitle = "Uses the latest weigh-in your scale app (like Libre Scale) saves to Health Connect, " +
                        "so calorie and protein targets follow your weight",
                    checked = settings.weightSync,
                ) { on ->
                    if (on) {
                        weightLauncher.launch(health.weightPermissions)
                    } else {
                        scope.launch { repo.setWeightSync(false) }
                    }
                }
                if (settings.weightSync && settings.lastWeighInTime > 0) {
                    Text(
                        "Last weigh-in: ${fmtWeight(settings.profile.weightKg, us)} on ${dateLabel(localDate(settings.lastWeighInTime))}" +
                            (if (settings.lastWeighInSource.isNotBlank()) " from ${settings.lastWeighInSource}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            Text(
                "Needs Health Connect, which isn't available on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
