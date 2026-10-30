package com.moblized.nutritrack.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moblized.nutritrack.data.NutrientDef
import com.moblized.nutritrack.data.NutrientGroup
import com.moblized.nutritrack.data.Nutrients
import com.moblized.nutritrack.data.Portion
import com.moblized.nutritrack.data.TargetKind
import com.moblized.nutritrack.ui.theme.color
import com.moblized.nutritrack.ui.theme.trackColor
import com.moblized.nutritrack.ui.theme.warningColors

/** Animated calorie ring with a gradient arc and a mood emoji. */
@Composable
fun CalorieRing(eaten: Double, target: Double, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val track = trackColor()
    val over = eaten > target * 1.05 && target > 0
    val raw = if (target > 0) (eaten / target).toFloat() else 0f
    val frac by animateFloatAsState(raw.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    val brush = if (over) {
        Brush.linearGradient(listOf(Color(0xFFFF8A65), MaterialTheme.colorScheme.error))
    } else {
        Brush.linearGradient(listOf(Color(0xFF36C47F), Color(0xFF16A6C9)))
    }
    val mood = when {
        eaten <= 0.0 -> "🍽️"
        over -> "🍕"
        raw >= 0.95f -> "🎯"
        raw >= 0.5f -> "💪"
        else -> "🌱"
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0.005f) {
                drawArc(brush, 135f, 270f * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mood, style = MaterialTheme.typography.titleLarge)
            val remaining = target - eaten
            Text(
                fmtKcal(kotlin.math.abs(remaining)),
                style = MaterialTheme.typography.headlineMedium,
                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (remaining < 0) "kcal over" else "kcal left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A rounded bar that grows smoothly when the value changes. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "bar")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(trackColor()),
    ) {
        if (animated > 0.001f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(height)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))),
            )
        }
    }
}

@Composable
fun MacroColumn(emoji: String, label: String, value: Double, target: Double?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("$emoji $label", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            buildString {
                append(fmtAmount(value))
                if (target != null && target > 0) append(" / ${fmtAmount(target)}")
                append(" g")
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(if (target != null && target > 0) (value / target).toFloat() else 0f, color, Modifier.fillMaxWidth())
    }
}

/** One nutrient with its emoji, amount, target and a progress bar. Met goals get a star. */
@Composable
fun NutrientRow(
    def: NutrientDef,
    value: Double,
    target: Double?,
    upperLimit: Double?,
    modifier: Modifier = Modifier,
    low: Boolean = false,
    lowLabel: String = "Running low",
) {
    val percent = pct(value, target)
    val overLimit = def.kind == TargetKind.LIMIT && target != null && value > target
    val overUl = upperLimit != null && value > upperLimit
    val met = def.kind == TargetKind.GOAL && percent != null && percent >= 100 && !overUl
    val barColor = when {
        overLimit || overUl -> MaterialTheme.colorScheme.error
        else -> def.group.color()
    }
    Column(modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Emoji.nutrient(def.id), modifier = Modifier.width(28.dp), maxLines = 1)
            Text(
                def.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (met) FontWeight.Bold else null,
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
                    if (met) "⭐$percent%" else "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        overLimit || overUl -> MaterialTheme.colorScheme.error
                        met -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp),
                    maxLines = 1,
                )
            }
        }
        if (def.kind != TargetKind.NONE && target != null && target > 0) {
            Spacer(Modifier.height(4.dp))
            ProgressBar((value / target).toFloat(), barColor, Modifier.fillMaxWidth().padding(start = 28.dp), height = 6.dp)
        }
        if (overUl) {
            Text(
                "Above the upper limit of ${fmtAmount(upperLimit!!)} ${def.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp, start = 28.dp),
            )
        } else if (overLimit) {
            Text(
                "Over your daily limit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp, start = 28.dp),
            )
        } else if (low) {
            Text(
                "⚠️ $lowLabel",
                style = MaterialTheme.typography.labelMedium,
                color = warningColors().accent,
                modifier = Modifier.padding(top = 2.dp, start = 28.dp),
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
    lowIds: Set<Int> = emptySet(),
    lowLabel: String = "Running low",
    /** False shows only the label essentials (calories, fats, carbs, protein, sodium, D, calcium, iron, potassium). */
    detailed: Boolean = true,
) {
    Column(modifier) {
        NutrientGroup.entries.forEach { group ->
            val defs = Nutrients.shown(detailed).filter { it.group == group }
                .filter { !(hideEmptyInfo && it.kind == TargetKind.NONE && (totals[it.id] ?: 0.0) == 0.0) }
            if (defs.isNotEmpty()) {
                GroupHeader(group)
                defs.forEach { def ->
                    NutrientRow(
                        def, totals[def.id] ?: 0.0, targets[def.id], upperLimits[def.id],
                        low = def.id in lowIds, lowLabel = lowLabel,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun GroupHeader(group: NutrientGroup) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        EmojiTile(Emoji.group(group), group.color().copy(alpha = 0.22f), size = 32.dp)
        Spacer(Modifier.width(10.dp))
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
