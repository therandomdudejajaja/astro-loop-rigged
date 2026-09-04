package com.astroloop.game.cabinet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class CabinetDebrisTest {

    private fun metrics() = CabinetMetrics(1080f, 2400f)

    /** A unit square centred on (500, 900), scaled up so drift is measurable. */
    private fun square(cx: Float, cy: Float, r: Float) = floatArrayOf(
        cx - r, cy - r,
        cx + r, cy - r,
        cx + r, cy + r,
        cx - r, cy + r
    )

    @Test fun aShatteredPolygonMakesOneSegmentPerEdge() {
        // The death animation IS the shape falling apart into the lines it was drawn
        // from, so the count has to be the edge count exactly - not a particle budget.
        val m = metrics()
        val pts = square(500f, 900f, 40f)
        val bits = CabinetDebris.shatter(500f, 900f, pts, pts.size, Random(1), m, 0f, 0f)
        assertEquals(4, bits.size)
    }

    @Test fun eachSegmentCarriesOneEdgesEndpoints() {
        val m = metrics()
        val pts = square(500f, 900f, 40f)
        val bits = CabinetDebris.shatter(500f, 900f, pts, pts.size, Random(2), m, 0f, 0f)
        // The square's edges are all 80 long; endpoints are stored relative to the
        // segment's own midpoint, so the span between them is the edge length.
        for (b in bits) {
            assertEquals(80f, hypot(b.x2 - b.x1, b.y2 - b.y1), 0.01f)
        }
    }

    @Test fun debrisDriftsOutwardFromTheCentre() {
        val m = metrics()
        val pts = square(500f, 900f, 40f)
        val bits = CabinetDebris.shatter(500f, 900f, pts, pts.size, Random(3), m, 0f, 0f)
        for (b in bits) {
            // Velocity must point away from the shape's centre, or the wreck implodes.
            val outX = b.x - 500f
            val outY = b.y - 900f
            assertTrue("segment at (${b.x}, ${b.y}) drifts inward",
                b.vx * outX + b.vy * outY > 0f)
        }
    }

    @Test fun debrisInheritsTheParentsVelocity() {
        val m = metrics()
        val pts = square(500f, 900f, 40f)
        val still = CabinetDebris.shatter(500f, 900f, pts, pts.size, Random(4), m, 0f, 0f)
        val moving = CabinetDebris.shatter(500f, 900f, pts, pts.size, Random(4), m, 300f, 0f)
        for (i in still.indices) {
            assertEquals(still[i].vx + 300f, moving[i].vx, 0.01f)
        }
    }

    @Test fun fadeRunsFromOneToZeroAcrossTheLifetime() {
        val m = metrics()
        val b = CabinetDebris(500f, 900f, 0f, 0f, -10f, 0f, 10f, 0f, 0f, 0f,
            CabinetDebris.LIFETIME)
        assertEquals(1f, b.fade, 0.001f)
        b.update(CabinetDebris.LIFETIME / 2f, m)
        assertEquals(0.5f, b.fade, 0.01f)
        b.update(CabinetDebris.LIFETIME, m)
        assertEquals(0f, b.fade, 0.001f)
        assertTrue("expired", b.life <= 0f)
    }

    @Test fun debrisWraps() {
        // Everything on this field is toroidal, and a rock destroyed at the seam throws
        // segments across it.
        val m = metrics()
        val b = CabinetDebris(5f, 5f, -600f, -600f, -10f, 0f, 10f, 0f, 0f, 0f,
            CabinetDebris.LIFETIME)
        b.update(0.1f, m)
        assertTrue("x should have wrapped: ${b.x}", b.x > m.width / 2f)
        assertTrue("y should have wrapped: ${b.y}", b.y > m.height / 2f)
    }

    @Test fun aDegeneratePolygonAtTheCentreStillGetsADirection() {
        // Guards a divide-by-zero: an edge midpoint exactly on the centre has no
        // outward direction to normalise.
        val m = metrics()
        val pts = floatArrayOf(500f, 900f, 500f, 900f, 500f, 900f)
        val bits = CabinetDebris.shatter(500f, 900f, pts, pts.size, Random(5), m, 0f, 0f)
        assertEquals(3, bits.size)
        for (b in bits) {
            assertTrue("must still drift somewhere", hypot(b.vx, b.vy) > 0f)
        }
    }
}
