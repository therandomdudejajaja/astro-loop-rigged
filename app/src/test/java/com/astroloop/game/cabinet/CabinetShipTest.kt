package com.astroloop.game.cabinet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CabinetShipTest {

    // A 1080-wide portrait playfield: the design reference. Every constant in
    // CabinetMetrics is a fraction of minEdge, so these figures scale by construction.
    private fun metrics() = CabinetMetrics(1080f, 2400f)

    private fun step(ship: CabinetShip, seconds: Float, ix: Float, iy: Float, input: Boolean) {
        val dt = 1f / 240f
        var t = 0f
        while (t < seconds) { ship.update(dt, ix, iy, input); t += dt }
    }

    /**
     * Distance travelled while coasting, accumulated per frame through the toroidal
     * delta. Measuring endpoint-minus-startpoint would break the moment a long coast
     * crosses a seam, which it now does: the ship reaches full speed slowly, so it is
     * much further down the field before it starts coasting than it used to be.
     */
    private fun coastDistance(ship: CabinetShip, m: CabinetMetrics, seconds: Float): Float {
        val dt = 1f / 240f
        var t = 0f
        var travelled = 0f
        while (t < seconds) {
            val py = ship.y
            ship.update(dt, 0f, 0f, false)
            travelled += abs(m.wrappedDelta(ship.y, py, m.height))
            t += dt
        }
        return travelled
    }

    @Test fun topSpeedMatchesTheMainGamesBaseSpeed() {
        // SHIP_BASE_SPEED is 300f. The cabinet deliberately matches it so the two
        // games feel related; only the drag differs.
        assertEquals(300f, metrics().topSpeed, 5f)
    }

    @Test fun velocityConvergesOnTheInputVectorRatherThanSnapping() {
        // The whole of the new control model. Velocity lerps toward the target at
        // VELOCITY_LERP, so half a second gets you roughly half way there, not all the
        // way. The old thrust-along-heading model was at top speed inside 0.2s.
        val m = metrics()
        val ship = CabinetShip(m)
        step(ship, 0.5f, 0f, -1f, true)
        assertEquals("1 - e^(-1.5*0.5) = 0.528", 0.528f * m.topSpeed, ship.speed(), 0.03f * m.topSpeed)
        step(ship, 2.5f, 0f, -1f, true)
        assertEquals("3s is 1 - e^(-4.5) = 0.989", m.topSpeed, ship.speed(), 0.02f * m.topSpeed)
    }

    @Test fun coastsToRestInAboutFourSeconds() {
        // The cabinet's feel, tuned longer at the owner's request: topSpeed / drag =
        // 0.28 / 0.070 = 4.0s. The main game halts in 0.75s; anything near that here
        // means the drag constant regressed.
        val ship = CabinetShip(metrics())
        step(ship, 3f, 0f, -1f, true)
        step(ship, 3.5f, 0f, 0f, false)
        assertTrue("still moving at 3.5s: ${ship.speed()}", ship.speed() > 20f)
        step(ship, 1.0f, 0f, 0f, false)
        assertEquals(0f, ship.speed(), 1f)
    }

    @Test fun coastCarriesOverHalfTheShortEdge() {
        // topSpeed^2 / (2 * drag) = 0.28^2 / (2 * 0.070) = 0.56 of minEdge.
        val m = metrics()
        val ship = CabinetShip(m)
        step(ship, 3f, 0f, -1f, true)
        val travelled = coastDistance(ship, m, 5f)
        assertEquals(0.56f * m.minEdge, travelled, 0.06f * m.minEdge)
    }

    @Test fun wrapsOnEveryEdge() {
        val m = metrics()
        val ship = CabinetShip(m)
        ship.x = -1f; ship.y = 10f; ship.update(1f / 240f, 0f, 0f, false)
        assertTrue("left edge should wrap to the right", ship.x > m.width - 50f)
        ship.x = m.width + 1f; ship.update(1f / 240f, 0f, 0f, false)
        assertTrue("right edge should wrap to the left", ship.x < 50f)
        ship.y = -1f; ship.update(1f / 240f, 0f, 0f, false)
        assertTrue("top edge should wrap to the bottom", ship.y > m.height - 50f)
        ship.y = m.height + 1f; ship.update(1f / 240f, 0f, 0f, false)
        assertTrue("bottom edge should wrap to the top", ship.y < 50f)
    }

    @Test fun headingTurnsGraduallyNotInstantly() {
        // Heading still lerps at the main game's own 8/s, and it still follows travel.
        // That is what keeps "shooting the crystal means flying at it" true in stage 2.
        val ship = CabinetShip(metrics())
        ship.heading = 0f
        ship.update(1f / 60f, 1f, 0f, true)
        val afterOneFrame = ship.heading
        assertTrue("should have started turning", afterOneFrame > 0.001f)
        assertTrue("should not have arrived in one frame", afterOneFrame < 1.2f)
    }

    @Test fun thrustIsAnalog() {
        val half = CabinetShip(metrics())
        val full = CabinetShip(metrics())
        step(half, 0.5f, 0f, -0.5f, true)
        step(full, 0.5f, 0f, -1f, true)
        assertEquals(full.speed() * 0.5f, half.speed(), 8f)
    }

    @Test fun distanceIsMeasuredAcrossTheSeamNotThroughTheMiddle() {
        // The field is a torus. A naive hypot says opposite edges are a whole screen
        // apart when they are actually touching, which would let bullets pass through
        // rocks at the seam and let the ship survive contacts it should not.
        val m = metrics()
        assertEquals(2f, m.distance(1f, 100f, m.width - 1f, 100f), 0.001f)
        assertEquals(2f, m.distance(100f, 1f, 100f, m.height - 1f), 0.001f)
        // And it still behaves normally away from the edges.
        assertEquals(30f, m.distance(100f, 100f, 130f, 100f), 0.001f)
    }

    @Test fun wrappedDeltaTakesTheShortWayRound() {
        val m = metrics()
        assertEquals(2f, m.wrappedDelta(1f, m.width - 1f, m.width), 0.001f)
        assertEquals(-2f, m.wrappedDelta(m.width - 1f, 1f, m.width), 0.001f)
        assertEquals(30f, m.wrappedDelta(130f, 100f, m.width), 0.001f)
    }

    @Test fun reversalDistanceIsFarShorterThanTheOldTurnDominatedFigure() {
        // Stage 2's fairness rule 1 measures this. Under the old model the ship had to
        // TURN before it could oppose its own travel and the turn dominated: 302 x
        // 0.375s + 26.6 = 140px. Velocity now lerps straight toward the input, so a
        // reversal is (v/k)(1 - ln2) = 62px. Patterns may be tighter accordingly.
        val m = metrics()
        val ship = CabinetShip(m)
        step(ship, 3f, 0f, -1f, true)
        val d = ship.reversalDistance()
        assertEquals(62f, d, 6f)
        assertTrue("must be well under the old 140px figure", d < 70f)
    }

    @Test fun aStationaryShipCommitsToNothing() {
        assertEquals(0f, CabinetShip(metrics()).reversalDistance(), 0.001f)
    }

    @Test fun reversalDistanceStaysCorrectBelowTopSpeed() {
        // The old formula assumed the commanded target equals -currentSpeed, which is
        // only true when currentSpeed IS topSpeed; update() always commands -topSpeed.
        // The two formulas coincide exactly at top speed, which is exactly why the
        // near-top-speed test above (reversalDistanceIsFarShorterThan...) could not
        // have caught this: it needs a mid-speed case, so set velocity directly rather
        // than running the ship up gradually.
        val m = metrics()
        val ship = CabinetShip(m)
        ship.vx = 0f
        ship.vy = -(m.topSpeed / 2f)
        // sp = T/2, k = 1.5, T = 302.4 (1080-wide reference):
        // distance = sp/k - (T/k)*ln((sp+T)/T) = 100.8 - 201.6*ln(1.5) = 19.06px.
        // The old (buggy) formula returned (sp/k)(1 - ln2) = 30.93px here — a 62%
        // overstatement.
        assertEquals(19.06f, ship.reversalDistance(), 0.05f)
    }
}
