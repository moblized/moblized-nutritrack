package com.moblized.nutritrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.data.NutrientDef
import com.moblized.nutritrack.data.NutrientGroup
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Portion
import com.moblized.nutritrack.data.TargetKind
import com.moblized.nutritrack.ui.theme.color

@Composable
fun CalorieRing(eaten: Double, target: Double, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val over = eaten > target && target > 0
    val color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val frac = if (target > 0) (eaten / target).toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0f) {
                drawArc(color, 135f, 270f * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val remaining = target - eaten
            Text(
                fmtKcal(kotlin.math.abs(remaining)),
                style = MaterialTheme.typography.headlineMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (over) "kcal over" else "kcal left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

@Composable
fun MacroColumn(label: String, value: Double, target: Double?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                append(fmtAmount(value))
                if (target != null && target > 0) append(" / ${fmtAmount(target)}")
                append(" g")
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(if (target != null && target > 0) (value / target).toFloat() else 0f, color, Modifier.fillMaxWidth())
    }
}

/** One nutrient with amount, target and a progress bar. */
@Composable
fun NutrientRow(
    def: NutrientDef,
    value: Double,
    target: Double?,
    upperLimit: Double?,
    modifier: Modifier = Modifier,
) {
    val percent = pct(value, target)
    val overLimit = def.kind == TargetKind.LIMIT && target != null && value > target
    val overUl = upperLimit != null && value > upperLimit
    val barColor = when {
        overLimit || overUl -> MaterialTheme.colorScheme.error
        else -> def.group.color()
    }
    Column(modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                def.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(fmtAmount(value))
                    if (target != null && def.kind != TargetKind.NONE) append(" / ${fmtAmount(target)}")
                    append(" ${def.unit}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (percent != null && def.kind != TargetKind.NONE) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (overLimit || overUl) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(52.dp).padding(start = 8.dp),
                    maxLines = 1,
                )
            }
        }
        if (def.kind != TargetKind.NONE && target != null && target > 0) {
            Spacer(Modifier.height(4.dp))
            ProgressBar((value / target).toFloat(), barColor, Modifier.fillMaxWidth(), height = 6.dp)
        }
        if (overUl) {
            Text(
                "Above the upper limit of ${fmtAmount(upperLimit!!)} ${def.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else if (overLimit) {
            Text(
                "Over your daily limit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Full breakdown grouped by category. Nutrients with no data are listed as 0 so gaps are visible. */
@Composable
fun NutrientBreakdown(
    totals: Map<Int, Double>,
    targets: Map<Int, Double>,
    upperLimits: Map<Int, Double>,
    modifier: Modifier = Modifier,
    hideEmptyInfo: Boolean = true,
) {
    Column(modifier) {
        NutrientGroup.entries.forEach { group ->
            val defs = Nutrients.all.filter { it.group == group }
                .filter { !(hideEmptyInfo && it.kind == TargetKind.NONE && (totals[it.id] ?: 0.0) == 0.0) }
            if (defs.isNotEmpty()) {
                GroupHeader(group)
                defs.forEach { def ->
                    NutrientRow(def, totals[def.id] ?: 0.0, targets[def.id], upperLimits[def.id])
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun GroupHeader(group: NutrientGroup) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(group.color()),
        )
        Spacer(Modifier.width(8.dp))
        Text(group.title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun UnitPicker(units: List<Portion>, selected: Portion, onSelect: (Portion) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                unitLabel(selected),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            units.forEach { u ->
                DropdownMenuItem(
                    text = { Text(unitLabel(u)) },
                    onClick = {
                        onSelect(u)
                        open = false
                    },
                )
            }
        }
    }
}

fun unitLabel(p: Portion): String = when (p.label) {
    "g", "oz" -> p.label
    else -> "${p.label} (${fmtAmount(p.grams)} g)"
}

@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            androidx.compose.material3.FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o)) },
            )
        }
    }
}
