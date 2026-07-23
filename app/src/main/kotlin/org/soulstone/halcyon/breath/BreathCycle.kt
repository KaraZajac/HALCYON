package org.soulstone.halcyon.breath

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pure, Android-free math behind the breathing animation. Kept isolated so
 * the whole timing model — phase, easing, orb fullness, the traveling dot's path
 * around the box — is deterministic and unit-testable (CI runs these tests).
 *
 * Box breathing (a.k.a. square breathing) is four equal phases of [PHASE_MS]:
 *   Inhale → Hold (full) → Exhale → Hold (empty), then repeat.
 */

const val PHASE_MS: Long = 4_000L
const val CYCLE_MS: Long = PHASE_MS * 4

enum class BreathPhase(val label: String) {
    Inhale("Breathe in"),
    HoldFull("Hold"),
    Exhale("Breathe out"),
    HoldEmpty("Hold");
}

/** A lightweight point so this file never touches androidx types. */
data class Pt(val x: Float, val y: Float)

/**
 * @param phase           which of the four phases we're in
 * @param phaseProgress   0f..1f progress through the current phase
 * @param secondsRemaining whole seconds left in the phase (4,3,2,1) — for the countdown
 * @param fullness        0f (lungs empty) .. 1f (lungs full), eased — drives the orb size
 * @param cycleProgress   0f..1f around the whole 16s loop — drives the box's traveling dot
 */
data class BreathState(
    val phase: BreathPhase,
    val phaseProgress: Float,
    val secondsRemaining: Int,
    val fullness: Float,
    val cycleProgress: Float,
)

/** Symmetric ease used for inhale/exhale so the breath accelerates then settles. */
fun easeInOutSine(t: Float): Float = (-(cos(PI * t) - 1.0) / 2.0).toFloat()

fun stateAt(elapsedMs: Long, phaseMs: Long = PHASE_MS): BreathState {
    val cycleMs = phaseMs * 4
    val inCycle = ((elapsedMs % cycleMs) + cycleMs) % cycleMs   // guard against negatives
    val phaseIndex = (inCycle / phaseMs).toInt().coerceIn(0, 3)
    val phase = BreathPhase.entries[phaseIndex]
    val p = (inCycle % phaseMs).toFloat() / phaseMs.toFloat()

    val fullness = when (phase) {
        BreathPhase.Inhale -> easeInOutSine(p)
        BreathPhase.HoldFull -> 1f
        BreathPhase.Exhale -> 1f - easeInOutSine(p)
        BreathPhase.HoldEmpty -> 0f
    }

    val secsLeft = (phaseMs / 1000L).toInt() - (p * (phaseMs / 1000L)).toInt()
    return BreathState(
        phase = phase,
        phaseProgress = p,
        secondsRemaining = secsLeft.coerceIn(1, (phaseMs / 1000L).toInt()),
        fullness = fullness,
        cycleProgress = inCycle.toFloat() / cycleMs.toFloat(),
    )
}

/**
 * Point on the perimeter of a rounded square centered at the origin, walked
 * clockwise from the top edge. [half] is the half-side; [corner] the corner
 * radius (clamped to [0, half]). [progress] is 0f..1f around the full loop.
 *
 * Used to place the glowing dot that traces the "box" — the literal square of
 * box breathing — at constant speed, one lap per 16s cycle.
 */
fun roundedRectPerimeterPoint(progress: Float, half: Float, corner: Float): Pt {
    val cr = corner.coerceIn(0f, half)
    val straight = 2f * (half - cr)            // length of one flat edge
    val arc = (PI / 2.0).toFloat() * cr        // length of one quarter-corner
    val perimeter = 4f * straight + 4f * arc
    var d = ((progress % 1f) + 1f) % 1f * perimeter

    // Walk clockwise: topEdge → TR corner → rightEdge → BR → bottomEdge → BL → leftEdge → TL.
    // Edges run between the corners' tangent points at ±(half - cr).
    val e = half - cr

    // top edge: from (-e,-half) to (e,-half)
    if (d <= straight) return Pt(-e + d, -half)
    d -= straight
    // top-right corner: center (e,-e), sweep -90°..0°
    if (d <= arc) { val a = -PI.toFloat() / 2f + d / cr; return Pt(e + cr * cos(a), -e + cr * sin(a)) }
    d -= arc
    // right edge: (half,-e) to (half,e)
    if (d <= straight) return Pt(half, -e + d)
    d -= straight
    // bottom-right corner: center (e,e), sweep 0°..90°
    if (d <= arc) { val a = d / cr; return Pt(e + cr * cos(a), e + cr * sin(a)) }
    d -= arc
    // bottom edge: (e,half) to (-e,half)
    if (d <= straight) return Pt(e - d, half)
    d -= straight
    // bottom-left corner: center (-e,e), sweep 90°..180°
    if (d <= arc) { val a = PI.toFloat() / 2f + d / cr; return Pt(-e + cr * cos(a), e + cr * sin(a)) }
    d -= arc
    // left edge: (-half,e) to (-half,-e)
    if (d <= straight) return Pt(-half, e - d)
    d -= straight
    // top-left corner: center (-e,-e), sweep 180°..270°
    val a = PI.toFloat() + d / cr
    return Pt(-e + cr * cos(a), -e + cr * sin(a))
}
