package org.soulstone.halcyon.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import org.soulstone.halcyon.breath.stateAt
import org.soulstone.halcyon.ui.theme.HalcyonCrust
import org.soulstone.halcyon.ui.theme.HalcyonGreenEmpty
import org.soulstone.halcyon.ui.theme.HalcyonGreenFull
import org.soulstone.halcyon.ui.theme.HalcyonGreenMid
import org.soulstone.halcyon.ui.theme.HalcyonGreenPeak
import org.soulstone.halcyon.ui.theme.HalcyonMint
import org.soulstone.halcyon.ui.theme.HalcyonSubtext
import org.soulstone.halcyon.ui.theme.HalcyonText
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val TAU = 6.2831855f

// The whole screen is fireflies. Depth-layered so the field reads as a space
// you're looking into, not a flat scatter.
private const val FIREFLIES = 420

/** Deterministic 0f..1f hash so the field is stable across frames. */
private fun hash(n: Int): Float {
    val s = sin(n * 127.1f + 31.7f) * 43758.545f
    return s - floor(s)
}

private fun frac(x: Float): Float = x - floor(x)

@Composable
fun BreathingScreen() {
    val running = remember { mutableStateOf(false) }
    val nowNs = remember { mutableStateOf(0L) }       // always advancing — the field never freezes
    val breathNs = remember { mutableStateOf(0L) }    // advances only while running — the breath clock

    // One frame clock drives everything. Accumulate raw nanos (no per-frame
    // truncation) so 16s box-breathing timing stays exact over long sessions.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) {
                    val dt = t - last
                    nowNs.value += dt
                    if (running.value) breathNs.value += dt
                }
                last = t
            }
        }
    }

    // Keep the screen awake during a session — no WAKE_LOCK permission needed.
    val view = LocalView.current
    DisposableEffect(running.value) {
        view.keepScreenOn = running.value
        onDispose { view.keepScreenOn = false }
    }

    // Blends the resting pulse into the guided breath as a session starts/stops.
    val runFactor by animateFloatAsState(
        targetValue = if (running.value) 1f else 0f,
        animationSpec = tween(1200),
        label = "run",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (running.value) {
                    running.value = false
                } else {
                    breathNs.value = 0L   // clean start on a fresh inhale
                    running.value = true
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nowMs = nowNs.value / 1_000_000f
            val breathMs = breathNs.value / 1_000_000f
            drawFireflies(nowMs, breathMs, runFactor)
        }

        Guidance(
            running = running.value,
            breathNs = breathNs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp),
        )
    }
}

private fun DrawScope.drawFireflies(nowMs: Float, breathMs: Float, runFactor: Float) {
    val w = size.width
    val h = size.height
    val minDim = size.minDimension
    val c = center
    val tSec = nowMs / 1000f

    val st = stateAt(breathMs.toLong())

    // Idle: a slow resting shimmer so the field is alive before a session begins.
    val idlePulse = 0.42f + 0.14f * sin(tSec * 0.35f)
    val fullness = (idlePulse + (st.fullness - idlePulse) * runFactor).coerceIn(0f, 1f)

    // GREEN whose vibrancy tracks the lungs: muted, low sage when empty; vivid
    // Catppuccin green when full, cresting to bright mint at the top of a breath.
    val base = if (fullness < 0.5f) {
        lerp(HalcyonGreenEmpty, HalcyonGreenMid, fullness / 0.5f)
    } else {
        lerp(HalcyonGreenMid, HalcyonGreenFull, (fullness - 0.5f) / 0.5f)
    }
    val crest = ((fullness - 0.72f) / 0.28f).coerceIn(0f, 1f)
    val accent = lerp(base, HalcyonGreenPeak, 0.55f * crest)

    // The breath is deliberately gentle now: the whole field swells only a few
    // percent, brightens, and its points grow slightly — no dramatic size swing.
    val breathScale = 0.965f + 0.055f * fullness
    val bright = 0.45f + 0.55f * fullness
    val sizeBreath = 0.92f + 0.16f * fullness

    // 1. A vast, edgeless bloom at the heart of the field — barely there, it
    //    just lets the middle of the night glow a little as the lungs fill.
    val bloomR = minDim * (0.40f + 0.12f * fullness)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.03f + 0.09f * fullness),
                accent.copy(alpha = 0f),
            ),
            center = c,
            radius = bloomR,
        ),
        radius = bloomR,
        center = c,
    )

    // 2. The fireflies. Each one owns: a depth (parallax size/speed/brightness),
    //    a slow upward float with seamless wrap, a two-octave sine wander so no
    //    path is ever straight, a dreamy blink, and — rarely — a long soft flare.
    val margin = minDim * 0.05f
    val span = h + 2f * margin
    for (i in 0 until FIREFLIES) {
        val d = hash(i * 7 + 1)                          // 0 = deep in the dark, 1 = near
        val d2 = d * d                                    // bias: most lights live deep

        // Slow rise on warm air — near ones drift faster (parallax); a lap of
        // the screen takes ~70s (near) to ~4min (far).
        val rise = 0.004f + 0.010f * d
        val yN = frac(hash(i * 7 + 3) - tSec * rise)
        val xN = hash(i * 7 + 2)

        // Two-octave wander, amplitude scaled by depth.
        val wAmp = minDim * (0.006f + 0.020f * d)
        val p1 = hash(i * 7 + 4) * TAU
        val p2 = hash(i * 7 + 5) * TAU
        val sp = 0.05f + 0.14f * hash(i * 7 + 6)
        val wx = sin(tSec * sp + p1) + 0.5f * sin(tSec * 0.31f + p2)
        val wy = cos(tSec * sp * 0.83f + p2) + 0.5f * sin(tSec * 0.23f + p1)

        var x = xN * w + wx * wAmp
        var y = yN * span - margin + wy * wAmp

        // The whole field breathes around the centre — gently.
        x = c.x + (x - c.x) * breathScale
        y = c.y + (y - c.y) * breathScale

        // Fade across the wrap seam so risers never pop in or out.
        val seam = (minOf(yN, 1f - yN) / 0.07f).coerceAtMost(1f)

        // Dreamy blink — slow, staggered, softened.
        val blink = 0.5f + 0.5f * sin(tSec * (0.35f + 0.75f * hash(i + 107)) + hash(i + 109) * TAU)
        val glow = blink * blink

        // Rare flare: a long triangular swell that visits each firefly on its
        // own 2–4 minute cycle, so somewhere on screen one is always blooming.
        val ff = frac(tSec * (0.0045f + 0.0055f * hash(i + 121)) + hash(i + 123))
        val flare = (1f - abs(ff - 0.035f) / 0.035f).coerceAtLeast(0f)

        val a = ((0.08f + 0.62f * glow) * (0.30f + 0.70f * d2) * bright * seam +
            0.55f * flare * bright * seam).coerceIn(0f, 1f)
        val sz = minDim * (0.0012f + 0.0050f * d) * sizeBreath * (1f + 1.1f * flare)

        // Colour variety: deep sage in the distance, the living green through the
        // middle, mint sparks, and the rare near-white ember.
        val cr = hash(i + 131)
        val col = when {
            cr > 0.94f -> lerp(accent, Color.White, 0.75f)
            cr > 0.70f -> lerp(accent, HalcyonMint, 0.60f)
            cr > 0.30f -> accent
            else -> lerp(accent, HalcyonGreenEmpty, 0.45f)
        }

        // Near fireflies carry a soft halo — two cheap circles, no gradient.
        if (d > 0.72f) {
            drawCircle(color = col.copy(alpha = a * 0.16f), radius = sz * 3.4f, center = Offset(x, y))
        }
        drawCircle(color = col.copy(alpha = a), radius = sz, center = Offset(x, y))
    }

    // 3. Vignette — the edges of the field sink into the night, keeping the eye
    //    resting softly at the centre.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, HalcyonCrust.copy(alpha = 0.55f)),
            center = c,
            radius = size.maxDimension * 0.72f,
        ),
        size = size,
    )
}

@Composable
private fun Guidance(
    running: Boolean,
    breathNs: androidx.compose.runtime.State<Long>,
    modifier: Modifier = Modifier,
) {
    if (!running) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Tap to begin",
                color = HalcyonText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Box breathing · 4–4–4–4",
                color = HalcyonSubtext,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    // derivedStateOf keeps the text off the per-frame recomposition path — it only
    // recomposes when the phase label or the whole-second countdown actually change.
    // Depends solely on breathNs (a stable State), never on the running flag.
    val label by remember { derivedStateOf { stateAt(breathNs.value / 1_000_000).phase.label } }
    val seconds by remember { derivedStateOf { stateAt(breathNs.value / 1_000_000).secondsRemaining } }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = HalcyonText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 6.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = seconds.toString(),
            color = HalcyonText,
            fontSize = 44.sp,
            fontWeight = FontWeight.Thin,
        )
    }
}
