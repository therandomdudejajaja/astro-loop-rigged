package com.astroloop.game.cabinet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The per-frame facts [CabinetCues] reads, published by the sim and the run.
 *
 * `CabinetSim` already carries a block of these — *"Per-frame event tallies, for the host's
 * audio"* — for the reasons its own comment gives: a discrete event cannot be recovered by
 * diffing collections across frames, because a spawn and a destruction in the same frame
 * cancel out. The crystal's three are the same problem and take the same shape.
 *
 * They are values rather than callbacks because `ReckoningRun` and `CabinetSim` may not touch
 * `SoundManager` — that purity is what lets the whole fight be tested.
 */
class CabinetCueSeamsTest {

    private val m = CabinetMetrics(1080f, 2400f)
    private fun sim() = CabinetSim(m, Random(13)).also { it.start() }

    /**
     * Drive the fight for [seconds] with the pilot doing nothing.
     *
     * Keep this SHORT. A stationary ship is not a survival proxy — it dies to SHATTER inside
     * a few seconds — and an eight-second pre-roll here made three of these tests read LOST
     * before they could reach the win they were about. The pre-roll only exists to prove the
     * flag was false beforehand; the fight itself is not what is under test.
     */
    private fun run(r: ReckoningRun, s: CabinetSim, seconds: Float) {
        val dt = 1f / 60f
        var t = 0f
        while (t < seconds) { r.update(dt, s); s.update(dt, 0f, 0f, false); t += dt }
    }

    // ── the crystal takes a hit ──────────────────────────────────────────

    /**
     * A cleared field with the crystal parked on the gun's own firing ray.
     *
     * The gun fires by itself at 6/sec — it is not gated on input — so no piloting is
     * needed, only something to shoot. The field is cleared through the real entrance
     * ([ReckoningRun.begin] on a phase jump) rather than by reaching into the sim, which
     * also suspends waves so a fresh one cannot spawn into the shot.
     */
    private fun crystalOnTheFiringRay(distance: Float = 300f): CabinetSim {
        val s = sim()
        ReckoningRun(m, startPhase = 1).begin(s)
        val sx = sin(s.ship.heading)
        val sy = -cos(s.ship.heading)
        val cx = ((s.ship.x + sx * distance) % m.width + m.width) % m.width
        val cy = ((s.ship.y + sy * distance) % m.height + m.height) % m.height
        s.placeCrystal(CabinetCrystal(cx, cy, 300))
        return s
    }

    private fun hitsOver(s: CabinetSim, frames: Int): Int {
        var hits = 0
        repeat(frames) { s.update(1f / 60f, 0f, 0f, false); hits += s.crystalHitsThisFrame }
        return hits
    }

    @Test fun aShotLandingOnTheCrystalIsCounted() {
        val s = crystalOnTheFiringRay()
        val hpBefore = s.crystal!!.hp
        assertTrue("the gun never landed a shot", hitsOver(s, 120) > 0)
        assertTrue("and it must be the same hit that did damage", s.crystal!!.hp < hpBefore)
    }

    @Test fun theHitTallyClearsOnTheNextFrame() {
        // Every neighbour in that block clears at the top of update(). One that latched
        // would retrigger its cue on every frame for the rest of the run.
        val s = crystalOnTheFiringRay()
        var framesWithAHit = 0
        var framesWithout = 0
        repeat(120) {
            s.update(1f / 60f, 0f, 0f, false)
            if (s.crystalHitsThisFrame > 0) framesWithAHit++ else framesWithout++
        }
        assertTrue("no hits landed at all", framesWithAHit > 0)
        assertTrue("a latched tally would never read zero again", framesWithout > 0)
    }

    @Test fun theCrystalsOwnShotsDoNotCountAsHitsOnIt() {
        // resolveBulletHits skips hostile bullets entirely; if that ever stopped being true
        // the crystal would applaud itself once a frame through the whole fight.
        val s = crystalOnTheFiringRay()
        val c = s.crystal!!
        repeat(20) { s.addHostileBullet(CabinetBullet(c.x, c.y, 0f, 0f, 9f, hostile = true)) }
        s.update(1f / 60f, 0f, 0f, false)
        assertEquals(0, s.crystalHitsThisFrame)
    }

    @Test fun shotsThatHitRocksAreNotCrystalHits() {
        // Free play: rocks everywhere and no crystal at all. The tally must stay at zero
        // while the gun is demonstrably landing shots on something.
        val s = sim()
        var destroyed = 0
        var crystalHits = 0
        repeat(600) {
            s.update(1f / 60f, 0f, 0f, false)
            destroyed += s.destroyedThisFrame.sum()
            crystalHits += s.crystalHitsThisFrame
        }
        assertTrue("the gun never destroyed a rock, so this proves nothing", destroyed > 0)
        assertEquals(0, crystalHits)
    }

    // ── the crystal emits ────────────────────────────────────────────────

    @Test fun theRunPublishesWhatThePatternPutOnTheFieldThisFrame() {
        val s = sim()
        val r = ReckoningRun(m, startPhase = 1)
        r.begin(s)
        var sawAnEmission = false
        var frames = 0
        val dt = 1f / 60f
        // Past the opening lull, into PULSE proper.
        while (frames < 60 * 12 && !sawAnEmission) {
            val before = s.bullets.count { it.hostile }
            r.update(dt, s)
            if (r.bulletsEmittedThisFrame > 0) {
                sawAnEmission = true
                assertEquals(
                    "the count must be what actually reached the field",
                    s.bullets.count { it.hostile } - before, r.bulletsEmittedThisFrame
                )
            }
            s.update(dt, 0f, 0f, false)
            frames++
        }
        assertTrue("PULSE never emitted in twelve seconds", sawAnEmission)
    }

    @Test fun aFrameThatEmitsNothingSaysNothing() {
        val s = sim()
        val r = ReckoningRun(m, startPhase = 1)
        r.begin(s)
        // The lull at the head of every pattern: five seconds in which nothing is emitted.
        r.update(1f / 60f, s)
        assertEquals(0, r.bulletsEmittedThisFrame)
    }

    @Test fun theEmissionCountClearsOnceTheFightIsOver() {
        // update() returns at the top once the outcome is decided. A count left standing
        // there would fire the emission cue every frame of the win hold.
        val s = sim()
        val r = ReckoningRun(m, startPhase = 5)
        r.begin(s)
        run(r, s, 1f)
        s.crystal?.damage(10_000)
        r.update(1f / 60f, s)
        assertEquals(ReckoningRun.Outcome.WON, r.outcome)
        r.update(1f / 60f, s)
        assertEquals(0, r.bulletsEmittedThisFrame)
    }

    // ── the crystal breaks ───────────────────────────────────────────────

    @Test fun theShatterIsAnnouncedOnTheFrameItHappens() {
        val s = sim()
        val r = ReckoningRun(m, startPhase = 5)
        r.begin(s)
        run(r, s, 1f)
        assertFalse("nothing has shattered yet", r.justShattered)
        s.crystal?.damage(10_000)
        r.update(1f / 60f, s)
        assertEquals(ReckoningRun.Outcome.WON, r.outcome)
        assertTrue("the win frame must announce the shatter", r.justShattered)
    }

    @Test fun theShatterIsAnnouncedExactlyOnce() {
        val s = sim()
        val r = ReckoningRun(m, startPhase = 5)
        r.begin(s)
        run(r, s, 1f)
        s.crystal?.damage(10_000)
        r.update(1f / 60f, s)
        assertTrue(r.justShattered)
        repeat(5) { r.update(1f / 60f, s) }
        assertFalse("a latched one-shot would play the ending on every held frame", r.justShattered)
    }

    @Test fun losingDoesNotAnnounceAShatter() {
        // The crystal outlives you; nothing of it breaks.
        val s = sim()
        val r = ReckoningRun(m, startPhase = 5)
        r.begin(s)
        run(r, s, 1f)
        // sim.over is the run's actual LOST condition, and endRun() is the public way to
        // reach it without a fifteen-second wait for a hostile bullet to find the ship.
        s.endRun()
        r.update(1f / 60f, s)
        assertEquals(ReckoningRun.Outcome.LOST, r.outcome)
        assertFalse(r.justShattered)
    }

    // ── the mapping from a real sim, which is where a swapped field would hide ──────────

    private val out = ArrayList<CabinetCues.Cue>()

    private fun cueIds(s: CabinetSim, r: ReckoningRun?, beat: Boolean = false, high: Boolean = false):
        List<String> {
        out.clear()
        CabinetCues.forFrame(s, r, beat, high, out)
        return out.map { it.eventId }
    }

    @Test fun eachRockSizeReachesItsOwnCue() {
        // The host used to index destroyedThisFrame by hand at three call sites. A swapped
        // index there is invisible to every unit test that builds a Frame itself, so this
        // drives a REAL sim and checks the correspondence on every single frame.
        val s = sim()
        val seen = HashSet<String>()
        repeat(3000) {
            s.update(1f / 60f, 0f, 0f, false)
            val ids = cueIds(s, null)
            for ((size, id) in listOf(
                RockSize.LARGE to "sfx_belt_break_lge",
                RockSize.MEDIUM to "sfx_belt_break_med",
                RockSize.SMALL to "sfx_belt_break_sml"
            )) {
                val broke = s.destroyedThisFrame[size.ordinal] > 0
                assertEquals("$size broke=$broke but cue=${ids.contains(id)}", broke, ids.contains(id))
                if (broke) seen.add(id)
            }
        }
        assertEquals("all three sizes must actually have been observed", 3, seen.size)
    }

    @Test fun theGunReachesItsCueFromARealSim() {
        val s = sim()
        var framesThatFired = 0
        repeat(120) {
            s.update(1f / 60f, 0f, 0f, false)
            val fired = s.shotsFiredThisFrame > 0
            assertEquals(fired, cueIds(s, null).contains("sfx_belt_fire"))
            if (fired) framesThatFired++
        }
        assertTrue("the gun never fired", framesThatFired > 0)
    }

    @Test fun freePlayHasNoCrystalCuesAtAll() {
        val s = sim()
        repeat(600) {
            s.update(1f / 60f, 0f, 0f, false)
            assertTrue(cueIds(s, null).none { it.startsWith("sfx_belt_crystal") })
        }
    }

    @Test fun theHeartbeatToneIsPassedThroughNotReDerived() {
        val s = sim()
        s.update(1f / 60f, 0f, 0f, false)
        assertTrue(cueIds(s, null, beat = true, high = true).contains("sfx_belt_beat_hi"))
        assertTrue(cueIds(s, null, beat = true, high = false).contains("sfx_belt_beat_lo"))
        assertTrue(cueIds(s, null, beat = false, high = true).none { it.startsWith("sfx_belt_beat") })
    }

    @Test fun aRealEmissionReachesTheCrystalsGunAtItsPatternsPitch() {
        val s = sim()
        val r = ReckoningRun(m, startPhase = 2)     // VOLLEY, index 1
        r.begin(s)
        var sawIt = false
        repeat(60 * 12) {
            r.update(1f / 60f, s)
            if (r.bulletsEmittedThisFrame > 0 && !sawIt) {
                sawIt = true
                out.clear()
                CabinetCues.forFrame(s, r, false, false, out)
                val cue = out.single { it.eventId == "sfx_belt_crystal_fire" }
                assertEquals(CabinetCues.rateForPhase(r.director.phaseIndex), cue.rate, 1e-6f)
            }
            s.update(1f / 60f, 0f, 0f, false)
        }
        assertTrue("VOLLEY never emitted", sawIt)
    }

    @Test fun aRealLandingReachesTheArrivalCue() {
        // The last cue that was wired straight into the view — which is why no seams test
        // covered it while the other one-shots were covered, and why its level sat outside
        // the ladder on playSFX's default argument.
        val s = sim()
        val r = ReckoningRun(m)
        r.begin(s)
        var sawIt = false
        repeat(60 * 30) {
            r.update(1f / 60f, s)
            if (r.justLanded && !sawIt) {
                sawIt = true
                assertTrue(cueIds(s, r).contains("sfx_crystal_activate"))
            }
            s.update(1f / 60f, 0f, 0f, false)
        }
        assertTrue("the crystal never landed", sawIt)
    }

    @Test fun aRealShatterReachesTheEndingCue() {
        val s = sim()
        val r = ReckoningRun(m, startPhase = 5)
        r.begin(s)
        run(r, s, 1f)
        s.crystal?.damage(10_000)
        r.update(1f / 60f, s)
        assertTrue(cueIds(s, r).contains("sfx_belt_crystal_shatter"))
    }

    @Test fun theLandingIsNotAnnouncedForeverIfTheRunEndsOnThatFrame() {
        // justLanded is the one one-shot assigned BELOW the outcome guard rather than
        // cleared above it, so it only latches when the loss lands on the SAME frame as the
        // touchdown — reachable, because the rocks are frozen during the flight but still
        // collide, and `sim.update()` runs before `run.update()` in the shell.
        //
        // Every later update() then returns at the guard with justLanded still true, and the
        // host's `if (shell.reckoning?.justLanded == true)` is not gated on screen: the
        // arrival sting retriggers every frame through GAME OVER until the cabinet closes.
        val dt = 1f / 60f

        // Which frame does it touch down on? Deterministic, so a second run can meet it.
        var landingFrame = -1
        run {
            val s = sim()
            val r = ReckoningRun(m)
            r.begin(s)
            var i = 0
            while (i < 60 * 30 && landingFrame < 0) {
                r.update(dt, s); s.update(dt, 0f, 0f, false)
                if (r.justLanded) landingFrame = i
                i++
            }
        }
        assertTrue("the crystal never landed", landingFrame >= 0)

        val s = sim()
        val r = ReckoningRun(m)
        r.begin(s)
        repeat(landingFrame) { r.update(dt, s); s.update(dt, 0f, 0f, false) }
        s.endRun()                    // the ship dies going into the touchdown frame
        r.update(dt, s)               // lands AND loses in one call
        assertTrue("this frame is meant to be the landing", r.justLanded)
        assertEquals(ReckoningRun.Outcome.LOST, r.outcome)

        repeat(3) { r.update(dt, s) }
        assertFalse("a latched landing replays the sting at frame rate", r.justLanded)
    }

    // ── the reckoning is not a scoring activity ──────────────────────────

    @Test fun theReckoningNeverScoresAPoint() {
        // Owner, device pass: "the crystal boss fight [should] not record any high scores,
        // it's a little weird that after the fight you get some points, that's not at all
        // the purpose of the fight."
        //
        // The score was never written to the arcade board — CabinetShell gates that on
        // !isReckoning — but it still ACCRUED and was still shown, because the authored
        // opening seeds a ring of rocks and shooting them pays. drawOver's comment even
        // claimed "the reckoning keeps no score - there are no rocks in it", which was
        // simply untrue. Now it is true.
        val s = sim()
        val r = ReckoningRun(m)
        r.begin(s)
        assertTrue("the opening is supposed to seed a field to fly through", s.rocks.isNotEmpty())
        var destroyed = 0
        repeat(60 * 40) {
            r.update(1f / 60f, s)
            s.update(1f / 60f, 0f, 0f, false)
            destroyed += s.destroyedThisFrame.sum()
        }
        assertTrue("the gun never broke a seeded rock, so this proves nothing", destroyed > 0)
        assertEquals("the reckoning must not pay out", 0, s.score)
    }

    @Test fun freePlayStillScoresNormally() {
        val s = sim()
        repeat(60 * 40) { s.update(1f / 60f, 0f, 0f, false) }
        assertTrue("free play must still be a score attack", s.score > 0)
    }

    @Test fun aFreshRunAfterAReckoningScoresAgain() {
        // The flag is per-run state like every other one start() clears; a reckoning that
        // left it off would silently make the next free-play run unscoreable.
        val s = sim()
        ReckoningRun(m).begin(s)
        s.start()
        repeat(60 * 40) { s.update(1f / 60f, 0f, 0f, false) }
        assertTrue("start() must restore scoring", s.score > 0)
    }
}
