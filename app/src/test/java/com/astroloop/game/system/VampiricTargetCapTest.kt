package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Vampiric Core drains the six nearest asteroids, not every asteroid in range.
 *
 * Uncapped, a field thick with debris turned the passive into unlimited sustain. Six keeps it
 * strong enough to out-heal Medic in the situation it is built for — a crowd pressing the ship —
 * without scaling with the crowd forever.
 */
class VampiricTargetCapTest {

    private lateinit var state: GameState
    private lateinit var ship: Ship
    private lateinit var system: VampiricLeecherSystem
    private val destroyed = mutableListOf<Asteroid>()

    @Before
    fun setup() {
        EntityPools.asteroids.freeAll()
        state = GameState()
        state.passiveStacks["vampiric_core"] = 1
        ship = Ship()
        ship.position.set(0f, 0f)
        ship.maxHealth = 100f
        ship.health = 50f
        destroyed.clear()
        system = VampiricLeecherSystem { destroyed.add(it) }
    }

    /** [count] asteroids ringed around the ship, all comfortably inside LEECH_RANGE. */
    private fun ring(count: Int): List<Asteroid> = (0 until count).map { i ->
        val a = EntityPools.asteroids.obtain()
        // Ordered by distance: index 0 nearest.
        a.initialize(10f + i * 2f, 0f, AsteroidSize.SMALL, AsteroidType.ROCK)
        a
    }

    @Test
    fun `at most six asteroids are drained in a tick`() {
        val asteroids = ring(12)
        // TICK_INTERVAL is 0.2s — one update at exactly that delta fires exactly one tick.
        system.update(ship, asteroids, state, VampiricLeecherSystem.TICK_INTERVAL)

        val drained = asteroids.count { it.health < it.maxHealth }
        assertEquals("only the six nearest may be drained", 6, drained)
    }

    @Test
    fun `the six drained are the nearest six`() {
        val asteroids = ring(12)
        system.update(ship, asteroids, state, VampiricLeecherSystem.TICK_INTERVAL)

        for (i in 0 until 6) {
            assertTrue("asteroid $i is among the nearest six and must be drained",
                asteroids[i].health < asteroids[i].maxHealth)
        }
        for (i in 6 until 12) {
            assertEquals("asteroid $i is outside the six and must be untouched",
                asteroids[i].maxHealth, asteroids[i].health, 0.0001f)
        }
    }

    @Test
    fun `the particle stream feeds from no more than six asteroids`() {
        val asteroids = ring(12)
        // One frame short of a tick: seeds the streams without draining.
        system.update(ship, asteroids, state, 0.001f)

        // Each newly in-range asteroid seeds 4 staggered particles.
        assertEquals("the visual must not show a feed the tick is not draining",
            6 * 4, system.particles.size)
    }

    @Test
    fun `healing is capped by the target cap`() {
        val asteroids = ring(12)
        val before = ship.health
        system.update(ship, asteroids, state, VampiricLeecherSystem.TICK_INTERVAL)

        val expected = VampiricLeecherSystem.LEECH_PER_STACK * 1 * 6
        assertEquals("heal must match six targets, not twelve",
            before + expected, ship.health, 0.0001f)
    }
}
