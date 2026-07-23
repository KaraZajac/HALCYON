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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import org.soulstone.halcyon.breath.PHASE_MS
import org.soulstone.halcyon.breath.roundedRectPerimeterPoint
import org.soulstone.halcyon.breath.stateAt
import org.soulstone.halcyon.ui.theme.HalcyonCrust
import org.soulstone.halcyon.ui.theme.HalcyonGreenEmpty
import org.soulstone.halcyon.ui.theme.HalcyonGreenFull
import org.soulstone.halcyon.ui.theme.HalcyonGreenMid
import org.soulstone.halcyon.ui.theme.HalcyonGreenPeak
import org.soulstone.halcyon.ui.theme.HalcyonMint
import org.soulstone.halcyon.ui.theme.HalcyonSubtext
import org.soulstone.halcyon.ui.theme.HalcyonText
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val TAU = 6.2831855f
private const val AMBIENT = 140      // drifting background motes
private const val RIM_SPARKS = 64    // embers lifting off the orb
private const val RIPPLE_LIFE_MS = 3_600f
private const val TAIL = 18

/** Deterministic 0f..1f hash so the particle field is stable across frames. */
private fun hash(n: Int): Float {
    val s = sin(n * 127.1f + 31.7f) * 43758.545f
    return s - floor(s)
}

private fun frac(x: Float): Float = x - floor(x)

@Composable
fun BreathingScreen() {
    val running = remember { mutableStateOf(false) }
    val nowNs = remember { mutableStateOf(0L) }       // always advancing — ambient motion / idle pulse
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

    // Fade the session-only layers (box, comet, ripples) in and out.
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
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nowMs = nowNs.value / 1_000_000f
            val breathMs = breathNs.value / 1_000_000f
            drawBreath(nowMs, breathMs, runFactor)
        }

        // Guidance text lives on the dark background below the box, never on the
        // orb — so it stays legible no matter how bright the breath gets.
        Guidance(
            running = running.value,
            breathNs = breathNs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
    }
}

private fun DrawScope.drawBreath(nowMs: Float, breathMs: Float, runFactor: Float) {
    val c = center
    val minDim = size.minDimension
    val tSec = nowMs / 1000f

    val st = stateAt(breathMs.toLong())

    // Idle: a slow resting pulse so the orb keeps breathing gently when paused.
    val idlePulse = 0.46f + 0.16f * sin(tSec * 0.55f)
    val fullness = (idlePulse + (st.fullness - idlePulse) * runFactor).coerceIn(0f, 1f)

    // GREEN, and its vibrancy tracks the breath: muted + dark when empty, vivid
    // + luminous when full. Two-stop ramp with a bright crest near a full inhale.
    val base = if (fullness < 0.5f) {
        lerp(HalcyonGreenEmpty, HalcyonGreenMid, fullness / 0.5f)
    } else {
        lerp(HalcyonGreenMid, HalcyonGreenFull, (fullness - 0.5f) / 0.5f)
    }
    val crest = ((fullness - 0.72f) / 0.28f).coerceIn(0f, 1f)
    val accent = lerp(base, HalcyonGreenPeak, 0.55f * crest)

    val orbMax = minDim * 0.29f
    val orbMin = orbMax * 0.40f
    val orbR = orbMin + (orbMax - orbMin) * fullness
    val boxHalf = minDim * 0.40f
    val boxCorner = boxHalf * 0.28f

    // 1. Breath glow — a soft radial bloom behind the orb that swells + brightens.
    val glowR = orbR * (2.4f + 0.6f * fullness)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.04f + 0.26f * fullness),
                accent.copy(alpha = 0f),
            ),
            center = c,
            radius = glowR,
        ),
        radius = glowR,
        center = c,
    )

    // 2. Ambient mote field — layered depth, drifting + twinkling, expanding and
    //    brightening as you inhale, contracting + dimming as you exhale.
    val breatheOut = 1f + 0.16f * fullness
    for (i in 0 until AMBIENT) {
        val depth = hash(i + 3)                       // 0 far .. 1 near (parallax)
        val baseAng = hash(i + 7) * TAU
        val baseRad = minDim * (0.15f + 0.68f * hash(i))
        val spin = (hash(i + 11) - 0.5f) * 0.06f * (0.4f + depth)
        val ang = baseAng + tSec * spin
        val bob = sin(tSec * (0.18f + 0.28f * hash(i + 5)) + i) * minDim * 0.014f
        val rad = baseRad * breatheOut + bob
        val pos = Offset(c.x + cos(ang) * rad, c.y + sin(ang) * rad)
        val twinkle = 0.5f + 0.5f * sin(tSec * (0.5f + hash(i + 13)) + i * 1.7f)
        val bright = 0.35f + 0.65f * fullness
        val a = (0.05f + 0.24f * depth) * (0.35f + 0.65f * twinkle) * bright
        val sz = minDim * (0.0012f + 0.0055f * depth) * (0.85f + 0.3f * fullness)
        val col = if (hash(i + 17) > 0.86f) HalcyonMint else accent
        drawCircle(color = col.copy(alpha = a), radius = sz, center = pos)
    }

    // 3. Rim sparks — embers that lift off the orb's surface and fade outward.
    //    Denser and brighter on the inhale, so the orb feels alive and radiant.
    for (j in 0 until RIM_SPARKS) {
        val life = frac(tSec * (0.09f + 0.07f * hash(j + 31)) + hash(j + 33))
        val ang = hash(j + 41) * TAU + tSec * 0.06f * (hash(j + 43) - 0.5f)
        val rad = orbR * (0.9f + life * (0.85f + 0.5f * fullness))
        val pos = Offset(c.x + cos(ang) * rad, c.y + sin(ang) * rad)
        val a = (1f - life) * (0.06f + 0.34f * fullness) * (0.5f + 0.5f * hash(j + 45))
        val sz = minDim * (0.0016f + 0.004f * hash(j + 47)) * (1f - 0.35f * life)
        drawCircle(color = HalcyonMint.copy(alpha = a), radius = sz, center = pos)
    }

    // 4. The box — faint rounded square the comet traces. Brightens with the session.
    drawRoundRectCentered(
        color = accent.copy(alpha = 0.05f + 0.13f * runFactor),
        center = c,
        half = boxHalf,
        corner = boxCorner,
        stroke = Stroke(width = minDim * 0.004f),
    )

    // 5. Ripples cast off at each phase boundary (only while a session runs).
    if (runFactor > 0.01f) {
        val lastBoundary = (breathMs / PHASE_MS).toInt()
        for (k in 0..3) {
            val bMs = (lastBoundary - k) * PHASE_MS.toFloat()
            val age = breathMs - bMs
            if (age in 0f..RIPPLE_LIFE_MS) {
                val f = age / RIPPLE_LIFE_MS
                val r = orbMax * (0.9f + 1.6f * f)
                drawCircle(
                    color = accent.copy(alpha = (1f - f) * 0.22f * runFactor),
                    radius = r,
                    center = c,
                    style = Stroke(width = minDim * 0.003f),
                )
            }
        }
    }

    // 6. The orb — a luminous green sphere: core, body, rim, and a soft highlight.
    //    Core whitens with the inhale so a full breath reads as radiant, not pale.
    val core = lerp(accent, Color.White, 0.14f + 0.20f * fullness)
    val edge = lerp(accent, HalcyonCrust, 0.5f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to core.copy(alpha = 0.96f),
                0.5f to accent.copy(alpha = 0.92f),
                1.0f to edge.copy(alpha = 0.9f),
            ),
            center = c,
            radius = orbR,
        ),
        radius = orbR,
        center = c,
    )
    // inner highlight, offset up-left, for a lit-from-above read
    val hl = Offset(c.x - orbR * 0.3f, c.y - orbR * 0.34f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.16f + 0.12f * fullness), Color.White.copy(alpha = 0f)),
            center = hl,
            radius = orbR * 0.6f,
        ),
        radius = orbR * 0.6f,
        center = hl,
    )
    drawCircle(
        color = lerp(accent, Color.White, 0.3f).copy(alpha = 0.35f + 0.2f * fullness),
        radius = orbR,
        center = c,
        style = Stroke(width = minDim * 0.0035f),
    )

    // 7. The traveling comet — a glowing dot with a fading tail, one lap per cycle.
    if (runFactor > 0.01f) {
        val cometCol = lerp(accent, HalcyonMint, 0.5f)
        val dotR = minDim * 0.012f
        for (k in TAIL downTo 1) {
            val tp = st.cycleProgress - k * 0.006f
            val pt = roundedRectPerimeterPoint(tp, boxHalf, boxCorner)
            val pos = Offset(c.x + pt.x, c.y + pt.y)
            val f = 1f - k.toFloat() / TAIL
            drawCircle(
                color = cometCol.copy(alpha = 0.45f * f * f * runFactor),
                radius = dotR * (0.4f + 0.6f * f),
                center = pos,
            )
        }
        val hp = roundedRectPerimeterPoint(st.cycleProgress, boxHalf, boxCorner)
        val head = Offset(c.x + hp.x, c.y + hp.y)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cometCol.copy(alpha = 0.6f * runFactor), cometCol.copy(alpha = 0f)),
                center = head,
                radius = dotR * 3.4f,
            ),
            radius = dotR * 3.4f,
            center = head,
        )
        drawCircle(lerp(cometCol, Color.White, 0.7f).copy(alpha = runFactor), dotR, head)
    }
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

// --- small local helpers -------------------------------------------------

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
