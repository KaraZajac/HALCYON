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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import org.soulstone.halcyon.breath.BreathPhase
import org.soulstone.halcyon.breath.PHASE_MS
import org.soulstone.halcyon.breath.roundedRectPerimeterPoint
import org.soulstone.halcyon.breath.stateAt
import org.soulstone.halcyon.ui.theme.HalcyonBlue
import org.soulstone.halcyon.ui.theme.HalcyonCrust
import org.soulstone.halcyon.ui.theme.HalcyonLavender
import org.soulstone.halcyon.ui.theme.HalcyonSapphire
import org.soulstone.halcyon.ui.theme.HalcyonSubtext
import org.soulstone.halcyon.ui.theme.HalcyonTeal
import org.soulstone.halcyon.ui.theme.HalcyonText
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val PARTICLES = 34
private const val RIPPLE_LIFE_MS = 3_600f
private const val TAIL = 16

// Anchor colour for each phase; the accent glides between neighbours so the hue
// rotates continuously through the breath rather than snapping at boundaries.
private val phaseAnchor = mapOf(
    BreathPhase.Inhale to HalcyonBlue,
    BreathPhase.HoldFull to HalcyonLavender,
    BreathPhase.Exhale to HalcyonTeal,
    BreathPhase.HoldEmpty to HalcyonSapphire,
)

/** Deterministic 0f..1f hash so the mote field is stable across frames/recompositions. */
private fun hash(n: Int): Float {
    val s = sin(n * 127.1f + 31.7f) * 43758.545f
    return s - floor(s)
}

@Composable
fun BreathingScreen() {
    val running = remember { mutableStateOf(false) }
    val nowNs = remember { mutableStateOf(0L) }       // always advancing — ambient motes / idle pulse
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

    // Fade the whole guidance layer as a session starts/stops.
    val runFactor by animateFloatAsState(
        targetValue = if (running.value) 1f else 0f,
        animationSpec = tween(700),
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
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nowMs = nowNs.value / 1_000_000f
            val breathMs = breathNs.value / 1_000_000f
            drawBreath(nowMs, breathMs, runFactor)
        }

        CenterText(running.value, breathNs)
    }
}

private fun DrawScope.drawBreath(nowMs: Float, breathMs: Float, runFactor: Float) {
    val c = center
    val minDim = size.minDimension
    val tSec = nowMs / 1000f

    val st = stateAt(breathMs.toLong())

    // Idle: a slow resting pulse so the orb keeps breathing gently when paused.
    val idlePulse = 0.5f + 0.12f * sin(tSec * 0.6f)
    val fullness = lerp01(idlePulse, st.fullness, runFactor)

    // Accent glides between the current phase's anchor and the next.
    val order = BreathPhase.entries
    val next = order[(st.phase.ordinal + 1) % order.size]
    val activeAccent = lerp(
        phaseAnchor.getValue(st.phase),
        phaseAnchor.getValue(next),
        easeInOut(st.phaseProgress),
    )
    val accent = lerp(HalcyonBlue, activeAccent, runFactor)

    val orbMax = minDim * 0.30f
    val orbMin = orbMax * 0.42f
    val orbR = orbMin + (orbMax - orbMin) * fullness
    val boxHalf = minDim * 0.40f
    val boxCorner = boxHalf * 0.28f

    // 1. Breath glow — a soft radial bloom behind the orb that swells with the inhale.
    val glowR = orbR * 2.7f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.05f + 0.20f * fullness),
                accent.copy(alpha = 0f),
            ),
            center = c,
            radius = glowR,
        ),
        radius = glowR,
        center = c,
    )

    // 2. Ambient mote field — drifting, twinkling, and it inhales with you.
    for (i in 0 until PARTICLES) {
        val baseRad = minDim * (0.14f + 0.52f * hash(i))
        val bob = sin(tSec * 0.3f + i) * minDim * 0.02f
        val rad = baseRad + bob
        val ang = hash(i + 10) * 6.2832f + tSec * (hash(i + 20) - 0.5f) * 0.06f
        val p = Offset(c.x + cos(ang) * rad, c.y + sin(ang) * rad)
        val twinkle = 0.5f + 0.5f * sin(tSec * (0.5f + hash(i + 30)) + i)
        val a = (0.10f + 0.22f * twinkle) * (0.6f + 0.4f * fullness)
        val dot = minDim * (0.0035f + 0.006f * hash(i + 40))
        drawCircle(color = accent.copy(alpha = a), radius = dot, center = p)
    }

    // 3. The box — faint rounded square the dot traces. Brightens with the session.
    drawRoundRectCentered(
        color = accent.copy(alpha = 0.06f + 0.12f * runFactor),
        center = c,
        half = boxHalf,
        corner = boxCorner,
        stroke = Stroke(width = minDim * 0.004f),
    )

    // 4. Ripples cast off at each phase boundary (only while a session runs).
    if (runFactor > 0.01f) {
        val lastBoundary = (breathMs / PHASE_MS).toInt()
        for (j in 0..3) {
            val bMs = (lastBoundary - j) * PHASE_MS.toFloat()
            val age = breathMs - bMs
            if (age in 0f..RIPPLE_LIFE_MS) {
                val f = age / RIPPLE_LIFE_MS
                val r = orbMax * (0.9f + 1.5f * f)
                drawCircle(
                    color = accent.copy(alpha = (1f - f) * 0.22f * runFactor),
                    radius = r,
                    center = c,
                    style = Stroke(width = minDim * 0.003f),
                )
            }
        }
    }

    // 5. The orb — a luminous sphere with core, body, rim and a soft highlight.
    val core = lerp(accent, Color.White, 0.55f)
    val edge = lerp(accent, HalcyonCrust, 0.45f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to core.copy(alpha = 0.97f),
                0.55f to accent.copy(alpha = 0.92f),
                1.0f to edge.copy(alpha = 0.88f),
            ),
            center = c,
            radius = orbR,
        ),
        radius = orbR,
        center = c,
    )
    // inner highlight, offset up-left, for a lit-from-above read
    val hl = Offset(c.x - orbR * 0.3f, c.y - orbR * 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0f)),
            center = hl,
            radius = orbR * 0.55f,
        ),
        radius = orbR * 0.55f,
        center = hl,
    )
    drawCircle(
        color = accent.copy(alpha = 0.45f),
        radius = orbR,
        center = c,
        style = Stroke(width = minDim * 0.0035f),
    )

    // 6. The traveling comet — a glowing dot with a fading tail, one lap per cycle.
    if (runFactor > 0.01f) {
        val dotR = minDim * 0.012f
        for (k in TAIL downTo 1) {
            val tp = st.cycleProgress - k * 0.006f
            val pt = roundedRectPerimeterPoint(tp, boxHalf, boxCorner)
            val pos = Offset(c.x + pt.x, c.y + pt.y)
            val f = 1f - k.toFloat() / TAIL
            drawCircle(
                color = accent.copy(alpha = 0.45f * f * f * runFactor),
                radius = dotR * (0.4f + 0.6f * f),
                center = pos,
            )
        }
        val hp = roundedRectPerimeterPoint(st.cycleProgress, boxHalf, boxCorner)
        val head = Offset(c.x + hp.x, c.y + hp.y)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.6f * runFactor), accent.copy(alpha = 0f)),
                center = head,
                radius = dotR * 3.2f,
            ),
            radius = dotR * 3.2f,
            center = head,
        )
        drawCircle(lerp(accent, Color.White, 0.7f).copy(alpha = runFactor), dotR, head)
    }
}

@Composable
private fun CenterText(
    running: Boolean,
    breathNs: androidx.compose.runtime.State<Long>,
) {
    if (!running) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Tap to begin",
                color = HalcyonText.copy(alpha = 0.92f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Box breathing · 4–4–4–4",
                color = HalcyonSubtext.copy(alpha = 0.7f),
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = HalcyonText.copy(alpha = 0.92f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = seconds.toString(),
            color = HalcyonText.copy(alpha = 0.85f),
            fontSize = 40.sp,
            fontWeight = FontWeight.Thin,
        )
    }
}

// --- small local helpers -------------------------------------------------

private fun lerp01(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

private fun easeInOut(t: Float): Float = (-(cos(Math.PI * t) - 1.0) / 2.0).toFloat()

private fun DrawScope.drawRoundRectCentered(
    color: Color,
    center: Offset,
    half: Float,
    corner: Float,
    stroke: Stroke,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - half, center.y - half),
        size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = stroke,
    )
}
