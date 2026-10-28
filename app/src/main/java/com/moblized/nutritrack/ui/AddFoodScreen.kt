package com.moblized.nutritrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.FoodSummary
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Meals
import com.moblized.nutritrack.data.OpenFoodFacts
import com.moblized.nutritrack.ui.theme.foodTileColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    date: LocalDate,
    meal: Int,
    initialQuery: String = "",
    onBack: () -> Unit,
    onOpenFood: (FoodSource, Long) -> Unit,
    onCreateFood: (String?) -> Unit,
    onCreateRecipe: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<FoodSummary>>(emptyList()) }
    var recent by remember { mutableStateOf<List<FoodSummary>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var lookingUp by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        recent = Graph.app.recentFoods()
        if (query.isBlank()) runCatching { focus.requestFocus() }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(180)
        results = Graph.app.search(query)
        searching = false
    }

    fun handleBarcode(code: String) {
        scope.launch {
            lookingUp = true
            val local = Graph.app.db.customFoods().byBarcode(code)
            if (local != null) {
                lookingUp = false
                onOpenFood(FoodSource.CUSTOM, local.id)
                return@launch
            }
            when (val r = OpenFoodFacts.lookup(code)) {
                is OpenFoodFacts.Result.Found -> {
                    val id = Graph.app.saveCustomFood(r.food)
                    lookingUp = false
                    onOpenFood(FoodSource.CUSTOM, id)
                }
                OpenFoodFacts.Result.NotFound -> {
                    lookingUp = false
                    onCreateFood(code)
                }
                is OpenFoodFacts.Result.Error -> {
                    lookingUp = false
                    snackbar.showSnackbar("Couldn't look up the barcode (${r.message}). Check your connection.")
                }
            }
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            )
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let { handleBarcode(it) } }
            .addOnFailureListener { e ->
                scope.launch { snackbar.showSnackbar("Scanner unavailable: ${e.message ?: "unknown error"}") }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${Emoji.meal(meal)} Add to ${Meals.name(meal)}")
                        Text(
                            dateLabel(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { scan() }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focus),
                placeholder = { Text("Search 15,000+ foods") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AssistChip(
                    onClick = { scan() },
                    label = { Text("Scan") },
                    leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                )
                AssistChip(
                    onClick = { onCreateFood(null) },
                    label = { Text("New food") },
                    leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null) },
                )
                AssistChip(
                    onClick = onCreateRecipe,
                    label = { Text("New recipe") },
                    leadingIcon = { Icon(Icons.Filled.Kitchen, contentDescription = null) },
                )
            }
            if (searching || lookingUp) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (lookingUp) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Looking up barcode…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            val showing = if (query.isBlank()) recent else results
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (query.isBlank() && recent.isEmpty() && !lookingUp) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("🥕🍳🥑", style = MaterialTheme.typography.displaySmall)
                            Text(
                                "What are you eating?",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                "Search above or scan a barcode. Foods you log show up here for one-tap adding.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (query.isBlank() && recent.isNotEmpty()) {
                    item {
                        Text(
                            "🕒 Recent",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                if (query.isNotBlank() && !searching && results.isEmpty()) {
                    item {
                        Text(
                            "🤔 No matches. Try fewer words (\"oats\" instead of \"rolled oats old fashioned\"), or create the food.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(showing, key = { "${it.source.key}-${it.id}" }) { f ->
                    FoodResultRow(f) { onOpenFood(f.source, f.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun FoodResultRow(f: FoodSummary, onClick: () -> Unit) {
    val category = if (f.source == FoodSource.USDA) f.detail.substringBefore(" · ") else null
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            EmojiTile(Emoji.food(f.name, category, f.source), foodTileColor(f.name), size = 42.dp)
        },
        headlineContent = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(f.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            f.kcalPer100?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtKcal(it), style = MaterialTheme.typography.titleSmall)
                    Text("kcal/100g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}
