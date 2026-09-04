package com.astroloop.game.cabinet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CabinetRockTest {

    private fun metrics() = CabinetMetrics(1080f, 2400f)

    @Test fun scoringIsTheClassicInversion() {
        // Smaller is worth more because smaller is harder to hit. Atari paid
        // 20/50/100 the same way round.
        assertEquals(1, RockSize.LARGE.points)
        assertEquals(2, RockSize.MEDIUM.points)
        assertEquals(3, RockSize.SMALL.points)
    }

    @Test fun largeSplitsIntoTwoMediumsAndMediumIntoTwoSmalls() {
        val m = metrics()
        val rng = Random(1)
        val large = CabinetRock(RockSize.LARGE, 100f, 100f, 0f, 0f,
            CabinetRock.generateShape(rng), 0f, 0f)
        val mediums = CabinetRock.split(large, rng, m)
        assertEquals(2, mediums.size)
        assertTrue(mediums.all { it.size == RockSize.MEDIUM })

        val smalls = CabinetRock.split(mediums[0], rng, m)
        assertEquals(2, smalls.size)
        assertTrue(smalls.all { it.size == RockSize.SMALL })

        assertTrue("smalls are terminal", CabinetRock.split(smalls[0], rng, m).isEmpty())
    }

    @Test fun oneLargeRockIsSevenDestructionsWorthSeventeenPoints() {
        // This is the arithmetic the whole scoring curve rests on: 1 + 2*2 + 4*3 = 17,
        // which puts 999 in wave 8. If this changes, the six-minute target moves.
        val destructions = 1 + 2 + 4
        val points = RockSize.LARGE.points + 2 * RockSize.MEDIUM.points + 4 * RockSize.SMALL.points
        assertEquals(7, destructions)
        assertEquals(17, points)
    }

    @Test fun shapeHasBetweenSevenAndElevenVerticesWithinVariance() {
        // Reuses the main game's Asteroid.generateShape parameters exactly:
        // Random.nextInt(7, 12) points at 0.3f radial variance.
        val rng = Random(7)
        repeat(50) {
            val s = CabinetRock.generateShape(rng)
            val n = s.size / 2
            assertTrue("vertex count $n out of range", n in 7..11)
            for (i in 0 until n) {
                val r = kotlin.math.hypot(s[i * 2], s[i * 2 + 1])
                assertTrue("radius $r outside 0.7..1.3", r in 0.69f..1.31f)
            }
        }
    }

    @Test fun shapeIsDeterministicForASeed() {
        assertArrayEquals(
            CabinetRock.generateShape(Random(42)),
            CabinetRock.generateShape(Random(42)),
            0.0001f
        )
    }

    @Test fun rocksWrap() {
        val m = metrics()
        val rock = CabinetRock(RockSize.SMALL, 5f, 5f, -600f, -600f,
            CabinetRock.generateShape(Random(3)), 0f, 0f)
        rock.update(0.1f, m)
        assertTrue(rock.x > m.width / 2f)
        assertTrue(rock.y > m.height / 2f)
    }

    @Test fun splitFragmentsInheritPositionAndDiverge() {
        val m = metrics()
        val rng = Random(9)
        val parent = CabinetRock(RockSize.LARGE, 300f, 400f, 20f, 0f,
            CabinetRock.generateShape(rng), 0f, 0f)
        val kids = CabinetRock.split(parent, rng, m)
        assertTrue(kids.all { it.x == 300f && it.y == 400f })
        assertNotEquals(kids[0].vx, kids[1].vx, 0.001f)
    }
}
