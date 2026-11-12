package com.moblized.nutritrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.data.AiException
import com.moblized.nutritrack.data.AiModel
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.OpenWebUiClient
import com.moblized.nutritrack.data.Settings
import com.moblized.nutritrack.data.nowMillis
import com.moblized.nutritrack.resources.Res
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Connects the user's own Open WebUI server, which reads receipt photos with a vision model. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun OpenWebUiSettings(settings: Settings) {
    val repo = Graph.app.settings
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(settings.aiUrl) }
    var key by remember { mutableStateOf(settings.aiKey) }
    var model by remember { mutableStateOf(settings.aiModel) }
    var models by remember { mutableStateOf<List<AiModel>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var sampleRunning by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchLine(
            title = "Read receipts with my AI",
            subtitle = if (settings.aiConfigured) {
                "Receipt photos go to ${settings.aiModel} on your server, which reads the items and spells out " +
                    "abbreviations. If it can't be reached, the phone reads the receipt itself."
            } else {
                "Connect your Open WebUI below to send receipt photos to your own model."
            },
            checked = settings.aiReadsReceipts,
        ) { on -> scope.launch { repo.setAiReceipts(on) } }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; status = null },
            label = { Text("Open WebUI address") },
            placeholder = { Text("https://ai.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; status = null },
            label = { Text("API key") },
            placeholder = { Text("sk-…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Box {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Vision model") },
                placeholder = { Text("qwen2.5vl:7b") },
                singleLine = true,
                trailingIcon = {
                    if (models.isNotEmpty()) {
                        IconButton(onClick = { picking = true }) { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose a model") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                models.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(if (m.name != m.id) "${m.name} (${m.id})" else m.id) },
                        onClick = {
                            model = m.id
                            picking = false
                            scope.launch { repo.setAiModel(m.id) }
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = url.isNotBlank() && key.isNotBlank() && !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        repo.setOpenWebUi(url, key)
                        try {
                            models = OpenWebUiClient(url, key).models()
                            if (model.isBlank()) model = models.firstOrNull { looksLikeVision(it.id) }?.id ?: ""
                            repo.setAiModel(model)
                            ok = true
                            status = "Connected · ${models.size} models" +
                                if (model.isBlank()) ". Choose a model that can see images." else ""
                            if (model.isBlank() && models.isNotEmpty()) picking = true
                        } catch (e: AiException) {
                            ok = false
                            status = e.message ?: "Couldn't connect"
                        }
                        testing = false
                    }
                },
            ) { Text(if (testing) "Connecting…" else "Save & load models") }
            if (settings.aiUrl.isNotBlank() || settings.aiKey.isNotBlank()) {
                TextButton(onClick = {
                    url = ""
                    key = ""
                    model = ""
                    models = emptyList()
                    status = null
                    scope.launch {
                        repo.setOpenWebUi("", "")
                        repo.setAiModel("")
                    }
                }) { Text("Disconnect") }
            }
        }
        if (settings.aiConfigured) {
            OutlinedButton(
                enabled = !sampleRunning && !testing,
                onClick = {
                    sampleRunning = true
                    ok = true
                    status = "Sending a sample receipt to ${settings.aiModel}…"
                    scope.launch {
                        val start = nowMillis()
                        status = try {
                            val photo = Res.readBytes("files/sample_receipt.jpg")
                            val read = OpenWebUiClient(settings.aiUrl, settings.aiKey).readReceipt(settings.aiModel, listOf(photo), "")
                            val secs = (nowMillis() - start) / 1000
                            val items = read.receipt.items
                            ok = items.isNotEmpty()
                            if (ok) {
                                "✓ ${settings.aiModel} read ${items.size} items in $secs s: " +
                                    items.joinToString(", ") { (if (it.quantity != 1.0) "${fmtQty(it.quantity)} × " else "") + (it.query ?: it.text) }
                            } else {
                                "${settings.aiModel} answered in $secs s but found no food on the sample receipt"
                            }
                        } catch (e: AiException) {
                            ok = false
                            e.message ?: "The test didn't work"
                        }
                        sampleRunning = false
                    }
                },
            ) { Text(if (sampleRunning) "Reading the sample…" else "Test with a sample receipt") }
        }
        if (model != settings.aiModel && model.isNotBlank()) {
            TextButton(onClick = { scope.launch { repo.setAiModel(model) } }) { Text("Use $model") }
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "Use a model that can see images, like qwen2.5vl, gemma3 or llama3.2-vision. Create an API key in " +
                "Open WebUI under Settings → Account → API keys (an admin may need to turn on API keys first, " +
                "under Admin → Settings → Authentication). Receipt photos only go to your server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Model names that usually mean the model can read images. */
private fun looksLikeVision(id: String): Boolean =
    Regex("vl|vision|llava|gemma3|minicpm-v|moondream|pixtral|qwen3-vl|mistral-small3", RegexOption.IGNORE_CASE).containsMatchIn(id)
