package com.astroloop.game.cabinet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class CabinetInputTest {

    // The reference playfield's figures, both derived from the main game's ratios at
    // 1080 design height. The radius is exact: 1080 * (120 / 960) = 1080 * 0.125 = 135.
    // The dead zone is rounded: 1080 * (JOYSTICK_DEAD_ZONE 20 / DESIGN_WIDTH 960) =
    // 1080 * 0.02083... ~= 1080 * 0.021 = 22.68, injected here as 22.7.
    private fun input() = CabinetInput(deadZone = 22.7f, maxRadius = 135f)

    @Test fun aFreshTouchProducesNoInput() {
        // The origin is planted where the finger lands, so at the moment of touch-down
        // the stick is centred. This is the whole reason the ship stopped lurching
        // toward the first tap of a double-tap.
        val i = input()
        i.down(500f, 900f)
        assertTrue(i.active)
        assertEquals(0f, i.x, 0.0001f)
        assertEquals(0f, i.y, 0.0001f)
    }

    @Test fun aStillFingerInsideTheDeadZoneIsGenuinelyNoInput() {
        val i = input()
        i.down(500f, 900f)
        i.move(510f, 900f)
        assertEquals(0f, hypot(i.x, i.y), 0.0001f)
    }

    @Test fun aRestingThumbIsDownButNotSteering() {
        // active and steering are not the same question, and the host asks the second
        // one in two places that must agree — the sim and the thrust sound took
        // `steering`, while the flame behind the ship took `active`, so a thumb parked
        // inside the dead zone drew an engine burning with no engine noise under it.
        val i = input()
        i.down(500f, 900f)
        assertTrue("a finger is down", i.active)
        assertFalse("but it is not asking for thrust", i.steering)

        i.move(560f, 900f)
        assertTrue("past the dead zone it is", i.steering)

        i.up()
        assertFalse("and a lifted finger never is", i.steering)
    }

    @Test fun theShipsPositionIsNeverConsulted() {
        // The defining property of a RELATIVE stick, and the reason the seam question
        // died: two gestures with identical deltas give identical input wherever they
        // happen on the glass.
        val a = input(); val b = input()
        a.down(100f, 100f); a.move(200f, 100f)
        b.down(900f, 2000f); b.move(1000f, 2000f)
        assertEquals(a.x, b.x, 0.0001f)
        assertEquals(a.y, b.y, 0.0001f)
    }

    @Test fun magnitudeRampsFromTheDeadZoneToFullDeflection() {
        val i = input()
        i.down(500f, 900f)
        // Just past the dead zone: barely any push.
        i.move(500f + 30f, 900f)
        val near = hypot(i.x, i.y)
        assertTrue("$near should be small", near > 0f && near < 0.15f)
        // Half way out.
        i.move(500f + (22.7f + 135f) / 2f, 900f)
        assertEquals(0.5f, hypot(i.x, i.y), 0.001f)
        // At the radius.
        i.move(500f + 135f, 900f)
        assertEquals(1f, hypot(i.x, i.y), 0.0001f)
    }

    @Test fun magnitudeClampsBeyondTheRadius() {
        val i = input()
        i.down(500f, 900f)
        i.move(500f + 900f, 900f)
        assertEquals(1f, hypot(i.x, i.y), 0.0001f)
        assertEquals(1f, i.x, 0.0001f)
        assertEquals(0f, i.y, 0.0001f)
    }

    @Test fun directionIsTheGestureDirection() {
        val i = input()
        i.down(500f, 900f)
        i.move(500f, 900f - 200f)
        assertEquals(0f, i.x, 0.0001f)
        assertEquals(-1f, i.y, 0.0001f)
    }

    @Test fun releaseInsideTheDeadZoneIsATap() {
        val i = input()
        i.down(500f, 900f)
        i.move(508f, 903f)
        assertTrue("never left the dead zone, so it is a tap", i.up())
        assertFalse(i.active)
    }

    @Test fun releaseAfterARealDragIsNotATap() {
        val i = input()
        i.down(500f, 900f)
        i.move(500f, 700f)
        assertFalse(i.up())
    }

    @Test fun releaseZeroesTheVectorSoTheShipCoasts() {
        val i = input()
        i.down(500f, 900f)
        i.move(500f, 700f)
        i.up()
        assertEquals(0f, hypot(i.x, i.y), 0.0001f)
    }

    @Test fun cancelAbandonsWithoutReportingATap() {
        val i = input()
        i.down(500f, 900f)
        i.cancel()
        assertFalse(i.active)
        assertEquals(0f, hypot(i.x, i.y), 0.0001f)
    }

    @Test fun moveBeforeDownIsIgnored() {
        val i = input()
        i.move(800f, 800f)
        assertFalse(i.active)
        assertEquals(0f, hypot(i.x, i.y), 0.0001f)
    }

    @Test fun upWithoutAPrecedingDownIsNotAPhantomTap() {
        // On a fresh instance originX/Y and currentX/Y are all zero, so the naive
        // hypot(0,0) < deadZone check would read as a tap. This is reachable in the
        // view layer: pressing the on-screen PLAY button flips the cabinet to its
        // play screen via the button hit-test path, which never calls down(). up()
        // still fires on that ACTION_UP. If it reported a tap here, the player's
        // first real tap would look like the second half of a double-tap and
        // immediately pause the run they just started. The guard must return false
        // when no gesture is active.
        val i = input()
        assertFalse(i.up())
        assertFalse(i.active)
    }

    @Test fun constructorRejectsAMaxRadiusThatDoesNotClearTheDeadZone() {
        // maxRadius == deadZone makes recompute()'s magnitude division 0f/0f = NaN,
        // and coerceIn lets NaN through since it compares false against both bounds.
        // Reject the nonsense at construction instead of guarding the hot path.
        try {
            CabinetInput(deadZone = 20f, maxRadius = 20f)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
