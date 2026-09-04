package com.astroloop.game.cabinet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class CabinetSimTest {

    private fun sim() =
        CabinetSim(CabinetMetrics(1080f, 2400f), Random(11)).also { it.start() }

    private fun run(s: CabinetSim, seconds: Float, ix: Float = 0f, iy: Float = 0f, input: Boolean = false) {
        val dt = 1f / 120f
        var t = 0f
        while (t < seconds) { s.update(dt, ix, iy, input); t += dt }
    }

    @Test fun waveOneSpawnsFourLargeRocks() {
        // Wave N spawns N+3 large. The curve this produces is what puts 999 in wave 8.
        val s = sim()
        assertEquals(1, s.wave)
        assertEquals(4, s.rocks.count { it.size == RockSize.LARGE })
    }

    @Test fun spawnCountIsCappedSoLateWavesDoNotFloodTheField() {
        // Wave 11 would otherwise be 77 simultaneous destructibles. The original
        // capped its spawns for the same reason.
        val s = sim()
        for (w in 1..14) {
            s.forceWave(w)
            assertTrue("wave $w spawned ${s.rocks.size}",
                s.rocks.count { it.size == RockSize.LARGE } <= CabinetSim.MAX_LARGE_PER_WAVE)
        }
    }

    @Test fun clearingTheFieldAdvancesTheWave() {
        // "Clearing" means the field is genuinely empty - fragments included. A new
        // wave must not spawn on top of the previous one's debris: the heartbeat
        // measures thinning against the wave's starting count, and the beat of quiet
        // between waves is the whole reason this game uses discrete waves.
        val s = sim()
        while (s.rocks.isNotEmpty() && s.wave == 1) {
            s.destroyForTest(s.rocks.first())
        }
        run(s, 0.05f)
        assertEquals(2, s.wave)
        assertEquals(5, s.rocks.count { it.size == RockSize.LARGE })
    }

    @Test fun destroyingALargeRockScoresOneAndLeavesTwoMediums() {
        val s = sim()
        val before = s.rocks.size
        s.destroyForTest(s.rocks.first { it.size == RockSize.LARGE })
        assertEquals(1, s.score)
        assertEquals(before + 1, s.rocks.size)
    }

    @Test fun clearingOneLargeRockFullyScoresSeventeen() {
        val s = sim()
        // Destroy exactly one large rock's whole lineage.
        val large = s.rocks.first { it.size == RockSize.LARGE }
        val lineage = ArrayDeque(listOf(large))
        var destroyed = 0
        while (lineage.isNotEmpty()) {
            val r = lineage.removeFirst()
            val kids = s.destroyForTest(r)
            lineage.addAll(kids)
            destroyed++
        }
        assertEquals(7, destroyed)
        assertEquals(17, s.score)
    }

    @Test fun scoreCapsAtNineNineNineAndPlayContinues() {
        // A three-digit readout is period-correct: it sticks rather than rolling over,
        // and the run still ends on death rather than on the cap.
        val s = sim()
        s.addScoreForTest(1200)
        assertEquals(CabinetSim.SCORE_CAP, s.score)
        assertFalse("hitting the cap must not end the run", s.over)
    }

    @Test fun cappedIsFlaggedTheInstantTheCeilingIsTouched() {
        val s = sim()
        assertFalse(s.capped)
        s.addScoreForTest(999)
        assertTrue(s.capped)
    }

    @Test fun collidingWithARockEndsTheRun() {
        val s = sim()
        val rock = s.rocks.first()
        s.ship.x = rock.x; s.ship.y = rock.y
        run(s, 0.05f)
        assertTrue(s.over)
        assertFalse(s.ship.alive)
    }

    @Test fun bulletsHitRocksAcrossTheSeam() {
        // Regression guard: with a naive hypot these are a full screen apart and the
        // shot sails straight through a rock it is touching.
        val s = sim()
        s.clearRocksForTest()
        s.placeRockForTest(RockSize.LARGE, 4f, 300f)
        s.placeBulletForTest(1076f, 300f)
        s.update(1f / 120f, 0f, 0f, false)
        assertEquals("the rock should have been hit", 2, s.rocks.size)
    }

    @Test fun theShipDiesToARockAcrossTheSeam() {
        val s = sim()
        s.clearRocksForTest()
        s.placeRockForTest(RockSize.LARGE, 4f, 300f)
        s.ship.x = 1078f; s.ship.y = 300f
        s.update(1f / 120f, 0f, 0f, false)
        assertTrue(s.over)
    }

    @Test fun bulletsExpireOnLifetimeRatherThanAccumulating() {
        val s = sim()
        run(s, 1f, 0f, -1f, true)
        val peak = s.bullets.size
        assertTrue("should be firing", peak > 0)
        run(s, 3f, 0f, -1f, true)
        assertTrue("bullet count must plateau, not grow without bound",
            s.bullets.size <= peak + 2)
    }

    @Test fun theOnScreenCapIsNeverExceeded() {
        // The original allowed four shots on screen and no more. Asserted every frame
        // rather than at the end, because a momentary overshoot is exactly the kind of
        // thing an end-state check sleeps through.
        val s = sim()
        val dt = 1f / 120f
        var t = 0f
        while (t < 6f) {
            s.update(dt, 0f, -1f, true)
            assertTrue("${s.bullets.size} bullets on screen", s.bullets.size <= CabinetSim.MAX_BULLETS)
            t += dt
        }
    }

    @Test fun theCapGovernsTheSustainedRateNotTheInterval() {
        // 4 slots / 1.2s lifetime = 3.33 shots/sec. The interval alone would give 6/sec,
        // so this range separates the two models rather than pinning a fragile number —
        // rocks being hit free slots early, which is play, not error.
        val s = sim()
        run(s, 2f, 0f, -1f, true)
        var shots = 0
        val dt = 1f / 120f
        var t = 0f
        while (t < 3f) { s.update(dt, 0f, -1f, true); shots += s.shotsFiredThisFrame; t += dt }
        assertTrue("$shots shots in 3s; uncapped would be ~18", shots in 8..15)
    }

    @Test fun theBulletCapIsTheOriginal1979MachineLimit() {
        // The original arcade limited shots to 4 on screen. Combined with the 1.2s
        // bullet lifetime, this governs the fire rate: 4 shots / 1.2s = 3.33 shots/sec.
        // There is no separate cadence constant to tune because the rate IS the cap.
        assertEquals(4, CabinetSim.MAX_BULLETS)
    }

    // --- muzzle: bullets leave from the nose, and wrap if that pushes them off-field ---

    @Test fun bulletsSpawnAtTheNoseNotTheShipsCentre() {
        // fireTimer starts at 0f, so the very first update() auto-fires - use that
        // frame-zero shot deliberately rather than fighting it: aim the ship before
        // calling update() once. Pass dt = 0f for that call so the bullet-update loop
        // that runs later in the same update() (which would otherwise carry the new
        // bullet forward by one frame's travel) contributes zero displacement - what's
        // left is exactly the muzzle offset fire() computed.
        val s = sim()
        s.ship.heading = (PI / 4).toFloat()
        val shipX = s.ship.x
        val shipY = s.ship.y
        s.update(0f, 0f, 0f, false)

        assertEquals(1, s.bullets.size)
        val nose = CabinetShip.HULL[0]
        // Derived from HULL, not hardcoded: the nose is HULL's first vertex, in ship
        // radii, so its distance from the hull's centre is this vertex's own length.
        val noseOffset = hypot(nose[0], nose[1]) * s.m.shipRadius
        val sx = sin(s.ship.heading)
        val sy = -cos(s.ship.heading)
        val expectedX = shipX + sx * noseOffset
        val expectedY = shipY + sy * noseOffset

        val bullet = s.bullets.first()
        assertEquals(expectedX, bullet.x, 0.01f)
        assertEquals(expectedY, bullet.y, 0.01f)
        assertTrue("bullet must not spawn at the ship's centre",
            abs(bullet.x - shipX) > 1f || abs(bullet.y - shipY) > 1f)
    }

    @Test fun theMuzzleWrapsWhenTheNoseCrossesTheEdge() {
        // Park the ship close enough to the left edge, facing further left, that the
        // nose offset alone pushes the spawn point past x = 0. Same dt = 0f trick as
        // above, isolating the muzzle offset from that frame's bullet travel.
        val s = sim()
        s.ship.x = 10f
        s.ship.y = 1200f
        s.ship.heading = -(PI / 2).toFloat() // sin = -1, cos = 0: facing -x
        s.update(0f, 0f, 0f, false)

        assertEquals(1, s.bullets.size)
        val nose = CabinetShip.HULL[0]
        val noseOffset = hypot(nose[0], nose[1]) * s.m.shipRadius
        var expectedX = 10f + sin(s.ship.heading) * noseOffset
        if (expectedX < 0f) expectedX += s.m.width
        if (expectedX >= s.m.width) expectedX -= s.m.width

        val bullet = s.bullets.first()
        assertTrue("muzzle must wrap onto the field rather than sit outside it",
            bullet.x >= 0f && bullet.x < s.m.width)
        assertEquals(expectedX, bullet.x, 0.01f)
    }

    @Test fun aDestroyedRockComesApartIntoItsOwnEdges() {
        // Nothing may vanish without a visible exit.
        val s = sim()
        val rock = s.rocks.first()
        val edges = rock.shape.size / 2
        s.destroyForTest(rock)
        assertEquals(edges, s.debris.size)
    }

    @Test fun theShipAndTheRockThatKilledItBothComeApart() {
        // Isolated to one rock so the count is exact. The killing rock does NOT go
        // through destroy(), so without its own shatter call it would blink out — the
        // one place the no-vanishing rule could still be breached.
        val s = sim()
        s.clearRocksForTest()
        s.placeRockForTest(RockSize.LARGE, 300f, 400f)
        val rockEdges = s.rocks.first().shape.size / 2
        // fireTimer starts at 0, so the very first update() auto-fires a bullet from the
        // ship's own position. Burn that shot here, with the ship still at its default
        // spot far from the rock, so it cannot also land on the rock we are about to
        // stand on — one frame's travel (~4.95px at 120fps) is well inside a LARGE
        // rock's radius (59.4px), so an un-primed test would let the bullet split the
        // rock into mediums a frame early and desync the "one rock" count below.
        s.update(1f / 120f, 0f, 0f, false)
        s.ship.x = 300f; s.ship.y = 400f
        s.update(1f / 120f, 0f, 0f, false)
        assertTrue(s.over)
        // The hull is a closed quad, so four edges. The original spec said three; that
        // text predates the notched hull being settled at four points.
        assertEquals(4 + rockEdges, s.debris.size)
    }

    @Test fun bulletsInFlightKeepFlyingAndClearAfterDeath() {
        // Shots already in the air when the ship dies must not hang frozen under GAME
        // OVER — the same "frozen frame" defect the wreck itself had. They fly on and
        // expire on their own lifetime.
        val s = sim()
        // Fly a moment so the autofire puts real bullets on the field, then ram a rock.
        run(s, 0.5f, 0f, -1f, true)
        assertTrue("expected bullets in flight before the death", s.bullets.isNotEmpty())
        val rock = s.rocks.first()
        s.ship.x = rock.x; s.ship.y = rock.y
        s.update(1f / 120f, 0f, 0f, false)
        assertTrue("the run should be over", s.over)

        val live = s.bullets.firstOrNull()
        assertNotNull("a bullet should have survived the death frame", live)
        val before = live!!.x to live.y
        s.update(1f / 60f, 0f, 0f, false)
        assertNotEquals("the bullet must keep travelling, not freeze", before, live.x to live.y)

        // And it must leave rather than sit there: one full lifetime clears the field.
        run(s, CabinetMetrics.BULLET_LIFETIME + 0.1f)
        assertTrue("bullets must expire after death, not accumulate", s.bullets.isEmpty())
    }

    @Test fun theWreckKeepsMovingAfterTheRunIsOver() {
        // The run ends but the animation must not freeze mid-air, or the ship vanishes
        // by another name. This is also what gives the attract demo its restart beat.
        val s = sim()
        val rock = s.rocks.first()
        s.ship.x = rock.x; s.ship.y = rock.y
        s.update(1f / 120f, 0f, 0f, false)
        val before = s.debris.first().let { it.x to it.y }
        s.update(1f / 60f, 0f, 0f, false)
        val after = s.debris.first().let { it.x to it.y }
        assertNotEquals(before, after)
    }

    @Test fun dyingToTheLastRockDoesNotMintAWaveBehindTheCorpse() {
        // The wave advance runs after resolveShipCollision() in the same update(), and
        // the collision removes the rock it killed the ship with. Unguarded, taking the
        // final rock of a wave with you spawned a whole new wave over your own wreckage
        // — a fresh WAVE N banner and N+3 rocks behind a corpse that cannot shoot.
        val s = sim()
        s.clearRocksForTest()
        // Below the ship: close enough to collide, far enough that the frame's own shot
        // (which leaves straight up) cannot reach it first and change what is under test.
        s.placeRockForTest(
            RockSize.SMALL,
            s.ship.x,
            s.ship.y + s.m.shipRadius + s.m.rockRadius(RockSize.SMALL) * 0.5f
        )
        val wave = s.wave
        s.update(1f / 60f, 0f, 0f, false)

        assertTrue("the ship should have died", s.over)
        assertTrue("that was the last rock", s.rocks.isEmpty())
        assertEquals("no new wave behind the corpse", wave, s.wave)
    }

    @Test fun debrisExpiresRatherThanAccumulating() {
        // Asserted against the ORIGINAL segments rather than an empty list: the gun
        // keeps firing during the wait, so a fresh kill can put new wreckage on screen
        // and an isEmpty() check would be flaky rather than wrong.
        val s = sim()
        s.destroyForTest(s.rocks.first())
        val original = s.debris.toList()
        assertTrue(original.isNotEmpty())
        run(s, CabinetDebris.LIFETIME + 0.2f)
        assertTrue("the original wreck must have cleared",
            original.none { it in s.debris })
    }

    @Test fun debrisNeverBlocksTheNextWave() {
        // Debris is not a rock. A field of drifting wreckage still counts as cleared.
        val s = sim()
        while (s.rocks.isNotEmpty()) s.rocks.toList().forEach { s.destroyForTest(it) }
        assertTrue("wreckage should be on screen", s.debris.isNotEmpty())
        s.update(1f / 120f, 0f, 0f, false)
        assertEquals(2, s.wave)
    }

    @Test fun eachWaveAnnouncesItselfThenClears() {
        val s = sim()
        assertEquals("wave 1 announces too", CabinetSim.WAVE_BANNER_SECONDS, s.waveBanner, 0.01f)
        run(s, CabinetSim.WAVE_BANNER_SECONDS + 0.2f)
        assertEquals(0f, s.waveBanner, 0.001f)
    }

    @Test fun theBannerRearmsOnTheNextWave() {
        val s = sim()
        run(s, CabinetSim.WAVE_BANNER_SECONDS + 0.2f)
        assertEquals(0f, s.waveBanner, 0.001f)
        while (s.rocks.isNotEmpty()) s.rocks.toList().forEach { s.destroyForTest(it) }
        s.update(1f / 120f, 0f, 0f, false)
        assertEquals(2, s.wave)
        assertTrue("wave 2 should announce itself", s.waveBanner > 0f)
    }

    @Test fun theBannerNeverBlocksPlay() {
        // It is an overlay, not a gate: rocks still move and the gun still fires while
        // it is up.
        val s = sim()
        val before = s.rocks.first().let { it.x to it.y }
        run(s, 0.3f, 0f, -1f, true)
        assertTrue(s.waveBanner > 0f)
        assertNotEquals(before, s.rocks.first().let { it.x to it.y })
        assertTrue(s.bullets.isNotEmpty())
    }

    // --- spawn safety: rocks must not materialise on the player ------------------

    private val safeSpawnRadius = 0.35f * CabinetMetrics(1080f, 2400f).minEdge

    @Test fun spawnedRocksClearASafeDistanceFromTheShip() {
        // "Spawn on an edge" is not "spawn away from the player" on a torus: x=0 is
        // adjacent to x=width. Wrapping is how the player escapes, so hugging an edge
        // is normal, skilful play - park the ship right on one and hammer forceWave at
        // the maximum wave size, 20 times over, with a fixed seed. Kotlin's Random(seed)
        // sequence is fixed for a given algorithm version, so this is reproducible run
        // to run rather than a one-in-a-while flake; 20 waves x up to 11 rocks gives the
        // edge-hugging bug hundreds of chances to place one on top of the ship, which
        // against the un-fixed code it reliably does (verified red below).
        val s = CabinetSim(CabinetMetrics(1080f, 2400f), Random(11)).also { it.start() }
        s.ship.x = 0f
        s.ship.y = 1200f
        for (w in 1..20) {
            s.forceWave(CabinetSim.MAX_LARGE_PER_WAVE)
            for (rock in s.rocks) {
                val d = s.m.distance(rock.x, rock.y, s.ship.x, s.ship.y)
                assertTrue(
                    "wave $w: rock at (${rock.x}, ${rock.y}) is only ${d}px from the ship " +
                        "at (${s.ship.x}, ${s.ship.y}); must clear ${safeSpawnRadius}px",
                    d >= safeSpawnRadius
                )
            }
        }
    }

    @Test fun aShipNearOneEdgeIsSafeFromSpawnsOnTheAdjacentOppositeEdge() {
        // Pins the TOROIDAL nature of the guarantee specifically, not just "some
        // distance check exists". Park the ship one pixel off the right edge: x=width-1
        // and x=0 are 1px apart by wraparound, but ~1079px apart by a naive hypot. A
        // "fix" that swapped m.distance for a raw hypot would see every x=0 candidate as
        // comfortably far and wave them through; measuring toroidally here would then
        // catch the rocks it lets land beside the ship.
        val s = CabinetSim(CabinetMetrics(1080f, 2400f), Random(29)).also { it.start() }
        s.ship.x = 1079f
        s.ship.y = 1200f
        for (w in 1..20) {
            s.forceWave(CabinetSim.MAX_LARGE_PER_WAVE)
            for (rock in s.rocks) {
                val d = s.m.distance(rock.x, rock.y, s.ship.x, s.ship.y)
                assertTrue(
                    "wave $w: rock at (${rock.x}, ${rock.y}) is only ${d}px from the ship " +
                        "at (${s.ship.x}, ${s.ship.y}) via wraparound; must clear ${safeSpawnRadius}px",
                    d >= safeSpawnRadius
                )
            }
        }
    }

    @Test fun spawnTerminatesAndFillsTheWaveEvenAtMaxSizeNearAnEdge() {
        // Hostile config: MAX_LARGE_PER_WAVE (11) rocks with the ship parked on an edge,
        // so a good chunk of candidates fail the safe-distance check on the first try
        // and the retry loop has to actually retry rather than always succeeding on
        // attempt one (which is all the default centre-ship setup would ever exercise).
        // If the retry cap or its fallback were missing or broken, this would hang or
        // under-spawn; it must do neither and still deliver exactly the requested count.
        val s = CabinetSim(CabinetMetrics(1080f, 2400f), Random(5)).also { it.start() }
        s.ship.x = 0f
        s.ship.y = 1200f
        s.forceWave(CabinetSim.MAX_LARGE_PER_WAVE)
        assertEquals(
            CabinetSim.MAX_LARGE_PER_WAVE,
            s.rocks.count { it.size == RockSize.LARGE }
        )
        s.rocks.forEach {
            assertTrue(s.m.distance(it.x, it.y, s.ship.x, s.ship.y) >= safeSpawnRadius)
        }
    }

    @Test fun theFallbackTerminatesEvenWhenNoCandidateCanEverBeSafe() {
        // 0.35 * minEdge is always achievable somewhere on the edges of a real,
        // finite field - the retry loop's fallback branch can never actually fire at
        // that fraction (see the report for the proof). To exercise it directly rather
        // than merely trust it, ask for a radius no field could ever satisfy and
        // confirm the retry cap still cuts the loop off and hands back a rock instead
        // of hanging or throwing.
        val s = sim()
        val impossibleRadius = s.m.minEdge * 100f
        val rock = s.spawnRockAwayFromShipForTest(impossibleRadius)
        assertNotNull(rock)
    }

    @Test fun startClearsTheFrozenFieldSoARetryDoesNotReplayAStillFrame() {
        // ReckoningOpening freezes the rocks for the crystal's flight in and thaws them on
        // landing. Die during those 4.5s and the flag is still set: AGAIN? runs start(),
        // which resets crystal, timeScale and wavesSuspended but not this — so the retry's
        // whole twelve-second authored opening played with motionless asteroids.
        val s = sim()
        s.rocksFrozen = true
        s.start()
        assertFalse("a fresh run must never begin frozen", s.rocksFrozen)
    }
}
