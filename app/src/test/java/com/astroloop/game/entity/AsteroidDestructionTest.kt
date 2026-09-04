package com.astroloop.game.entity

import com.astroloop.game.core.GameState
import com.astroloop.game.system.SpawnSystem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * An asteroid may only be destroyed once.
 *
 * CollisionSystem gathers every projectile hit for the frame before any damage is applied, and
 * `takeDamage` reports `health <= 0` rather than "died on this call". Without a one-shot claim, a
 * piercing volley destroys the same rock once per hit — each one spawning fragments, a yen pickup
 * and a drop roll.
 */
class AsteroidDestructionTest {

    private lateinit var state: GameState
    private lateinit var spawnSystem: SpawnSystem

    @Before
    fun setup() {
        EntityPools.asteroids.freeAll()
        state = GameState()
        spawnSystem = SpawnSystem(EntityPools.asteroids)
        spawnSystem.initialize(1080f, 2400f)
    }

    private fun newLarge(): Asteroid {
        val a = EntityPools.asteroids.obtain()
        a.initialize(500f, 500f, AsteroidSize.LARGE, AsteroidType.ROCK)
        return a
    }

    @Test
    fun `the first claim succeeds and deactivates the asteroid`() {
        val asteroid = newLarge()
        assertTrue("a live asteroid must yield its destruction", asteroid.claimDestruction())
        assertFalse("claiming destruction deactivates the asteroid", asteroid.isActive)
    }

    @Test
    fun `a second claim is refused`() {
        val asteroid = newLarge()
        asteroid.claimDestruction()
        assertFalse("the same asteroid must not be destroyed twice", asteroid.claimDestruction())
    }

    @Test
    fun `only the first claim spawns fragments`() {
        val parent = newLarge()
        var fragments = 0
        // Three damage sources resolving against one already-dead rock in the same frame.
        repeat(3) {
            if (parent.claimDestruction()) {
                fragments += spawnSystem.spawnSplitAsteroids(parent, state).size
            }
        }
        assertEquals("a LARGE rock splits into exactly two MEDIUMs, once", 2, fragments)
    }
}
