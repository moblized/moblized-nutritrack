package com.moblized.nutritrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.data.FoodSource
import com.moblized.nutritrack.data.Graph
import com.moblized.nutritrack.data.Meals
import com.moblized.nutritrack.data.NutrientCodec
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.OcrLine
import com.moblized.nutritrack.data.ParsedReceipt
import com.moblized.nutritrack.data.ReceiptItem
import com.moblized.nutritrack.data.ReceiptMatch
import com.moblized.nutritrack.data.ReceiptMatcher
import com.moblized.nutritrack.data.ReceiptParser
import com.moblized.nutritrack.data.RecipeItem
import com.moblized.nutritrack.data.scaled
import com.moblized.nutritrack.data.sumNutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** The text of the receipt just scanned, handed from the scanner to the review screen. */
object ReceiptDraft {
    var lines: List<OcrLine> = emptyList()
}

data class ReceiptRow(
    /** What the receipt says, with its quantity ("2 × Big Mac"). */
    val label: String,
    val item: RecipeItem?,
    val include: Boolean,
    /** Every word of the line was found in the food's name. */
    val confident: Boolean,
    val estimated: Boolean,
)

fun receiptLabel(item: ReceiptItem): String =
    (if (item.quantity != 1.0) "${fmtQty(item.quantity)} × " else "") + item.text +
        (item.pieces?.let { " ($it pc)" } ?: "")

/** Review rows for one receipt line ("Chips & Guac" gives two). */
fun receiptRows(item: ReceiptItem, results: List<ReceiptMatch>): List<ReceiptRow> {
    if (results.isEmpty()) return listOf(ReceiptRow(receiptLabel(item), null, include = false, confident = false, estimated = false))
    return results.map { r ->
        val label = if (results.size > 1) receiptLabel(item) + " → " + r.text else receiptLabel(item)
        ReceiptRow(label, r.item, include = r.item != null && r.confident, confident = r.confident, estimated = r.estimatedAmount)
    }
}

@Composable
fun ReceiptScreen(date: LocalDate, meal: Int, onBack: () -> Unit, onLogged: (Int) -> Unit) {
    var parsed by remember { mutableStateOf<ParsedReceipt?>(null) }
    val rows = remember { mutableStateListOf<ReceiptRow>() }
    val skipped = remember { mutableStateListOf<String>() }
    var matched by remember { mutableIntStateOf(0) }
    var matching by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var pickIndex by remember { mutableStateOf<Int?>(null) }
    var matcher by remember { mutableStateOf<ReceiptMatcher?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun matchInto(item: ReceiptItem, p: ParsedReceipt, m: ReceiptMatcher) {
        val results = withContext(Dispatchers.IO) {
            runCatching { m.match(item, p.store, p.mexican) }.getOrDefault(emptyList())
        }
        rows += receiptRows(item, results)
    }

    LaunchedEffect(Unit) {
        val p = withContext(Dispatchers.Default) { ReceiptParser.parse(ReceiptParser.rows(ReceiptDraft.lines)) }
        parsed = p
        skipped.clear()
        skipped += p.skipped
        val m = ReceiptMatcher(Graph.app.usda, Graph.app.personalFoods())
        matcher = m
        rows.clear()
        for (item in p.items) {
            matchInto(item, p, m)
            matched++
        }
        matching = false
    }

    ReceiptReview(
        date = date,
        meal = meal,
        loaded = parsed != null,
        itemCount = parsed?.items?.size ?: 0,
        matched = matched,
        matching = matching,
        rows = rows,
        skipped = skipped,
        saving = saving,
        onBack = onBack,
        onToggle = { i, on -> rows.getOrNull(i)?.let { rows[i] = it.copy(include = on && it.item != null) } },
        onEdit = { i -> if (rows.getOrNull(i)?.item != null) editIndex = i else pickIndex = i },
        onChangeFood = { i -> pickIndex = i },
        onAddSkipped = { line ->
            // Something the reader wrongly left out: match it like any other line.
            val p = parsed
            val m = matcher
            if (p != null && m != null) {
                skipped.remove(line)
                scope.launch { matchInto(ReceiptItem(line), p, m) }
            }
        },
        onLog = {
            saving = true
            scope.launch {
                val n = Graph.app.logItems(date, meal, rows.filter { it.include }.mapNotNull { it.item })
                saving = false
                onLogged(n)
            }
        },
    )

    editIndex?.let { i ->
        val row = rows.getOrNull(i)
        val item = row?.item
        if (row == null || item == null) {
            editIndex = null
        } else {
            IngredientAmountDialog(
                source = FoodSource.of(item.source),
                id = item.refId,
                initial = item,
                onDismiss = { editIndex = null },
                onConfirm = { updated ->
                    rows[i] = row.copy(item = updated, include = true, confident = true, estimated = false)
                    editIndex = null
                },
            )
        }
    }

    pickIndex?.let { i ->
        val row = rows.getOrNull(i)
        if (row == null) {
            pickIndex = null
        } else {
            IngredientPicker(
                excludeRecipeId = 0L,
                title = "Choose food",
                initialQuery = row.label.substringAfterLast(" → ").substringAfter(" × ").substringBefore(" ("),
                onDismiss = { pickIndex = null },
                onPicked = { item ->
                    rows[i] = row.copy(item = item, include = true, confident = true, estimated = false)
                    pickIndex = null
                },
            )
        }
    }
}

/** The receipt's matches to check before logging them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReview(
    date: LocalDate,
    meal: Int,
    loaded: Boolean,
    itemCount: Int,
    matched: Int,
    matching: Boolean,
    rows: List<ReceiptRow>,
    skipped: List<String>,
    saving: Boolean,
    onBack: () -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onEdit: (Int) -> Unit,
    onChangeFood: (Int) -> Unit,
    onAddSkipped: (String) -> Unit,
    onLog: () -> Unit,
) {
    var showSkipped by remember { mutableStateOf(false) }
    val included = rows.filter { it.include && it.item != null }.mapNotNull { it.item }
    val total = sumNutrients(included.map { NutrientCodec.decode(it.per100).scaled(it.grams / 100.0) })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🧾 Receipt")
                        Text(
                            "Add to ${Meals.name(meal)} · ${dateLabel(date)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            if (!matching && rows.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "${fmtKcal(total[Nutrients.ENERGY] ?: 0.0)} kcal · " +
                                "P ${fmtAmount(total[Nutrients.PROTEIN] ?: 0.0)} g · " +
                                "C ${fmtAmount(total[Nutrients.CARBS] ?: 0.0)} g · " +
                                "F ${fmtAmount(total[Nutrients.FAT] ?: 0.0)} g",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Button(
                            enabled = included.isNotEmpty() && !saving,
                            onClick = onLog,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                if (included.size == 1) "Log 1 item to ${Meals.name(meal)}"
                                else "Log ${included.size} items to ${Meals.name(meal)}",
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        when {
            !loaded -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            itemCount == 0 && rows.isEmpty() && !matching -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("🧾", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "No food found on this receipt",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "Try again with the receipt flat, in good light and filling the frame.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Back") }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    if (matching) {
                        Text(
                            "Matching $itemCount items to foods… $matched of $itemCount",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { if (itemCount == 0) 1f else matched.toFloat() / itemCount },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    } else {
                        Text(
                            "Check each match, then log them. Tap a row to change the amount (did you share it?), " +
                                "or untick anything you didn't eat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(rows, key = { i, _ -> "row-$i" }) { index, row ->
                    ReceiptRowView(
                        row = row,
                        onToggle = { on -> onToggle(index, on) },
                        onEditAmount = { onEdit(index) },
                        onChangeFood = { onChangeFood(index) },
                    )
                }
                if (!matching && skipped.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSkipped = !showSkipped }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                "Lines that aren't food (${skipped.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(if (showSkipped) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                    }
                    if (showSkipped) {
                        itemsIndexed(skipped.toList(), key = { i, _ -> "skip-$i" }) { _, line ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { onAddSkipped(line) },
                            ) {
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                )
                                Icon(Icons.Filled.Add, contentDescription = "Add as food", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRowView(
    row: ReceiptRow,
    onToggle: (Boolean) -> Unit,
    onEditAmount: () -> Unit,
    onChangeFood: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.clickable(onClick = onEditAmount).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Checkbox(checked = row.include, onCheckedChange = onToggle, enabled = row.item != null)
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium)
                val item = row.item
                if (item == null) {
                    Text(
                        "No match found. Tap to choose a food.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val kcal = (NutrientCodec.decode(item.per100)[Nutrients.ENERGY] ?: 0.0) * item.grams / 100
                    Text(
                        "${amountText(item.quantity, item.unitLabel, item.grams)} · ${fmtKcal(kcal)} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        !row.confident -> Text(
                            "Best guess. Check it's right, then tick it to log it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        row.estimated -> Text(
                            "Serving size is a guess. Tap to check.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    TextButton(onClick = onChangeFood, contentPadding = PaddingValues(0.dp)) {
                        Text("Change food")
                    }
                }
            }
        }
    }
}
