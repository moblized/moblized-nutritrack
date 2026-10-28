package com.moblized.nutritrack.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sin
import kotlin.random.Random

private val confettiColors = listOf(
    Color(0xFF36C47F), Color(0xFFFFC233), Color(0xFFFF6B8B), Color(0xFF6C8CFF),
    Color(0xFF16A6C9), Color(0xFFB06CFF), Color(0xFFFF8A3D),
)

private class Piece(
    val x: Float, // 0..1 of width
    val vx: Float, // dp per second
    val vy: Float, // dp per second (downwards)
    val sway: Float,
    val phase: Float,
    val spin: Float,
    val w: Float,
    val h: Float,
    val round: Boolean,
    val color: Color,
)

private const val DURATION_MS = 3200

/**
 * A burst of confetti that falls from the top of the screen whenever [trigger] changes to a new
 * non-zero value. Skipped when the phone's animations are turned off.
 */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val context = LocalContext.current
    val animationsOn = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
    if (!animationsOn) return

    val pieces = remember(trigger) {
        val r = Random(trigger * 7919L)
        List(150) {
            Piece(
                x = r.nextFloat(),
                vx = (r.nextFloat() - 0.5f) * 160f,
                vy = 120f + r.nextFloat() * 260f,
                sway = 8f + r.nextFloat() * 22f,
                phase = r.nextFloat() * 6.28f,
                spin = (r.nextFloat() - 0.5f) * 900f,
                w = 6f + r.nextFloat() * 6f,
                h = 9f + r.nextFloat() * 9f,
                round = r.nextFloat() < 0.25f,
                color = confettiColors[r.nextInt(confettiColors.size)],
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }
    if (progress.value >= 1f) return

    Canvas(modifier.fillMaxSize()) {
        val t = progress.value * DURATION_MS / 1000f
        val dp = density
        val gravity = 380f
        val fade = if (progress.value > 0.75f) 1f - (progress.value - 0.75f) / 0.25f else 1f
        for (p in pieces) {
            val x = p.x * size.width + (p.vx * t + sin(t * 3f + p.phase) * p.sway) * dp
            val y = (-24f + p.vy * t + 0.5f * gravity * t * t) * dp
            if (y > size.height + 40f) continue
            translate(x, y) {
                rotate(p.spin * t, pivot = Offset.Zero) {
                    val w = p.w * dp
                    val h = p.h * dp
                    if (p.round) {
                        drawCircle(p.color.copy(alpha = fade), radius = w / 2, center = Offset.Zero)
                    } else {
                        drawRect(p.color.copy(alpha = fade), topLeft = Offset(-w / 2, -h / 2), size = Size(w, h))
                    }
                }
            }
        }
    }
}
