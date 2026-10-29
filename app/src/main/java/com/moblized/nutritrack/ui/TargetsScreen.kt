package com.moblized.nutritrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.NutrientGroup
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.TargetKind
import com.moblized.nutritrack.data.Targets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(settings: Settings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val defaults = Targets.defaults(settings)
    val texts = remember {
        mutableStateMapOf<Int, String>().apply {
            settings.overrides.forEach { (id, v) -> put(id, fmtQty(v)) }
        }
    }
    val editable = Nutrients.shown(settings.detailedNutrition).filter {
        it.kind != TargetKind.NONE && it.id != Nutrients.ENERGY && defaults[it.id] != null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrient targets") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = {
                        texts.clear()
                        scope.launch { Graph.app.settings.clearOverrides() }
                    }) { Text("Reset all") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                Text(
                    "Leave a field blank to use the recommended amount shown underneath. " +
                        "Calories and macros are set on the Settings screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            NutrientGroup.entries.forEach { group ->
                val defs = editable.filter { it.group == group }
                if (defs.isNotEmpty()) {
                    item(key = "h-${group.name}") { GroupHeader(group) }
                    items(defs, key = { it.id }) { def ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(def.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    (if (def.kind == TargetKind.LIMIT) "Limit " else "Recommended ") +
                                        "${fmtAmount(defaults[def.id] ?: 0.0)} ${def.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedTextField(
                                value = texts[def.id] ?: "",
                                onValueChange = { v ->
                                    texts[def.id] = v
                                    val n = parseNumber(v)
                                    if (v.isBlank()) {
                                        scope.launch { Graph.app.settings.setOverride(def.id, null) }
                                    } else if (n != null && n > 0) {
                                        scope.launch { Graph.app.settings.setOverride(def.id, n) }
                                    }
                                },
                                singleLine = true,
                                placeholder = { Text(fmtAmount(defaults[def.id] ?: 0.0)) },
                                suffix = { Text(def.unit) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(140.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
