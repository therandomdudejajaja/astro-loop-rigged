package com.astroloop.game.cabinet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class CrystalDeathTest {

    private val m = CabinetMetrics(1080f, 2400f)

    private fun fightSim(): CabinetSim {
        val s = CabinetSim(m, Random(99))
        s.start()
        s.clearField()
        s.wavesSuspended = true
        s.placeCrystal(CabinetCrystal(m.width / 2f, m.height / 2f, 10))
        return s
    }

    @Test fun aRockStillShattersExactlyAsItDid() {
        // The defaulted parameters must be bit-for-bit no-ops for every existing caller.
        val s = CabinetSim(m, Random(4))
        s.start()
        val before = s.rocks.size
        s.destroyForTest(s.rocks.first())
        assertTrue(s.debris.isNotEmpty())
        assertTrue("rock debris keeps the original lifetime",
            s.debris.all { it.life <= CabinetDebris.LIFETIME + 0.001f })
        assertEquals(before - 1 + 2, s.rocks.size)
    }

    @Test fun theCrystalComesApartIntoManyPieces() {
        val s = fightSim()
        s.shatterCrystal()
        assertEquals(
            CabinetCrystal.SHELL_POINTS * CabinetSim.CRYSTAL_DEBRIS_SUBDIVISIONS,
            s.debris.size
        )
        assertTrue("this is the ending, not a rock", s.debris.size >= 60)
    }

    @Test fun thePieceCountIsCappedForFrameTime() {
        // drawDebris is one drawPath per piece per wrap image, against a menu already
        // measured at ~1000 calls, and frame time is a standing complaint. Recursive
        // shattering was rejected for this; the cap stops it coming back by increment.
        assertTrue(
            "crystal debris must stay inside the frame budget",
            CabinetCrystal.SHELL_POINTS * CabinetSim.CRYSTAL_DEBRIS_SUBDIVISIONS <= 100
        )
    }

    @Test fun thePiecesLingerFarLongerThanARocksDo() {
        val s = fightSim()
        s.shatterCrystal()
        assertTrue(s.debris.all { it.life > CabinetDebris.LIFETIME * 3f })
        assertTrue(s.debris.all { it.life <= CabinetSim.CRYSTAL_DEBRIS_LIFE + 0.001f })
    }

    @Test fun thePiecesAreThrownAcrossTheField() {
        // A rock's fragments travel ~32px before they fade. The crystal's must cross the
        // screen, or "big" is only a word in the design doc.
        val s = fightSim()
        s.shatterCrystal()
        val slowest = s.debris.minOf { hypot(it.vx, it.vy) }
        val rockDrift = CabinetDebris.DRIFT_FRAC * m.minEdge
        assertTrue("slowest piece at $slowest must beat a rock's drift",
            slowest > rockDrift * 2f)
        assertTrue("and must cross a good part of the field in its lifetime",
            slowest * CabinetSim.CRYSTAL_DEBRIS_LIFE > m.minEdge * 0.5f)
    }

    @Test fun theCrystalIsGoneOnceItHasShattered() {
        val s = fightSim()
        s.shatterCrystal()
        assertNull("the wreck IS the exit; the body must not linger under it", s.crystal)
    }

    @Test fun timeScaleSlowsTheWholeSim() {
        val s = fightSim()
        s.placeShip(200f, 200f)
        val startY = s.ship.y
        s.timeScale = 0.25f
        repeat(60) { s.update(1f / 60f, 0f, 1f, true) }
        val slowTravel = kotlin.math.abs(s.ship.y - startY)

        val f = fightSim()
        f.placeShip(200f, 200f)
        repeat(60) { f.update(1f / 60f, 0f, 1f, true) }
        val fullTravel = kotlin.math.abs(f.ship.y - 200f)

        assertTrue("a quarter-speed sim must travel far less", slowTravel < fullTravel * 0.5f)
    }

    @Test fun timeScaleIsOneAfterAFreshStart() {
        val s = fightSim()
        s.timeScale = 0.2f
        s.start()
        assertEquals(1f, s.timeScale, 0.0001f)
    }
}
