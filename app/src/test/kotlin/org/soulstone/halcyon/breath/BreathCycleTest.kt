package org.soulstone.halcyon.breath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BreathCycleTest {

    @Test fun phasesLandOnTheRightQuarters() {
        assertEquals(BreathPhase.Inhale, stateAt(0).phase)
        assertEquals(BreathPhase.Inhale, stateAt(3_999).phase)
        assertEquals(BreathPhase.HoldFull, stateAt(4_000).phase)
        assertEquals(BreathPhase.Exhale, stateAt(8_000).phase)
        assertEquals(BreathPhase.HoldEmpty, stateAt(12_000).phase)
        // wraps cleanly into the next cycle
        assertEquals(BreathPhase.Inhale, stateAt(16_000).phase)
    }

    @Test fun fullnessRunsEmptyToFullAndBack() {
        assertEquals(0f, stateAt(0).fullness, 1e-4f)          // empty at the inhale's start
        assertEquals(0.5f, stateAt(2_000).fullness, 1e-3f)    // half-full mid-inhale (eased)
        assertEquals(1f, stateAt(4_000).fullness, 1e-4f)      // full through the hold
        assertEquals(1f, stateAt(8_000).fullness, 1e-4f)      // still full at the exhale's start
        assertEquals(0f, stateAt(12_000).fullness, 1e-4f)     // empty through the empty-hold
    }

    @Test fun countdownCountsDownWholeSeconds() {
        assertEquals(4, stateAt(0).secondsRemaining)
        assertEquals(3, stateAt(1_000).secondsRemaining)
        assertEquals(1, stateAt(3_500).secondsRemaining)
    }

    @Test fun negativeElapsedIsHandled() {
        // Guard against a clock that briefly reads below zero.
        assertEquals(BreathPhase.HoldEmpty, stateAt(-1).phase)
    }

    @Test fun cycleProgressWrapsMonotonically() {
        assertEquals(0f, stateAt(0).cycleProgress, 1e-4f)
        assertTrue(stateAt(8_000).cycleProgress in 0.49f..0.51f)
    }

    @Test fun perimeterStartsOnTopEdgeAndStaysInBounds() {
        val half = 100f
        val corner = 28f
        val start = roundedRectPerimeterPoint(0f, half, corner)
        assertEquals(-half, start.y, 1e-3f)
        assertTrue(abs(start.x) <= half)

        // Walk the whole loop; every point must stay on/inside the square's bounds.
        var i = 0
        while (i <= 400) {
            val p = roundedRectPerimeterPoint(i / 400f, half, corner)
            assertTrue("x out of bounds at $i", abs(p.x) <= half + 1e-2f)
            assertTrue("y out of bounds at $i", abs(p.y) <= half + 1e-2f)
            i++
        }
    }
}
