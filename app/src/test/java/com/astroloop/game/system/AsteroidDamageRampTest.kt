package com.astroloop.game.system

import com.astroloop.game.entity.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Asteroid contact damage ramps in Astro Loop, mirroring the health ramp.
 *
 * Health was the only unclamped lever, which made late Astro Loop a wall the player could not break
 * but also could not die to: a strong build simply stopped being able to lose. Damage now carries
 * the other half.
 */
class AsteroidDamageRampTest {

    @Test
    fun `no bonus outside astro loop`() {
        assertEquals(0f, SpawnSystem.asteroidDamageBonus(3600f, astroLoopMode = false), 0.0001f)
    }

    @Test
    fun `no bonus before the ramp starts`() {
        assertEquals(0f, SpawnSystem.asteroidDamageBonus(0f, true), 0.0001f)
        assertEquals(0f, SpawnSystem.asteroidDamageBonus(
            SpawnSystem.DAMAGE_RAMP_START_MINUTES * 60f, true), 0.0001f)
    }

    @Test
    fun `the bonus grows per minute past the start`() {
        val oneMinutePast = (SpawnSystem.DAMAGE_RAMP_START_MINUTES + 1f) * 60f
        assertEquals(SpawnSystem.DAMAGE_RAMP_PER_MINUTE,
            SpawnSystem.asteroidDamageBonus(oneMinutePast, true), 0.0001f)
    }

    @Test
    fun `the bonus is capped`() {
        assertEquals(SpawnSystem.DAMAGE_RAMP_MAX_BONUS,
            SpawnSystem.asteroidDamageBonus(60f * 60f * 10f, true), 0.0001f)
    }

    @Test
    fun `contact damage carries the bonus`() {
        val asteroid = Asteroid()
        asteroid.initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK)
        assertEquals(Asteroid.BASE_CONTACT_DAMAGE, asteroid.getContactDamage(), 0.0001f)

        asteroid.damageBonus = 12f
        assertEquals(Asteroid.BASE_CONTACT_DAMAGE + 12f, asteroid.getContactDamage(), 0.0001f)
    }

    @Test
    fun `volatile explosion damage carries the bonus`() {
        val asteroid = Asteroid()
        asteroid.initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.VOLATILE)
        asteroid.damageBonus = 10f
        assertEquals((Asteroid.BASE_CONTACT_DAMAGE + 10f) * 1.5f,
            asteroid.getExplosionDamage(), 0.0001f)
    }

    @Test
    fun `trail damage scales in proportion, not by a flat bonus`() {
        val asteroid = Asteroid()
        asteroid.initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.TRAIL)
        val base = asteroid.getTrailDamage()

        asteroid.damageBonus = Asteroid.BASE_CONTACT_DAMAGE  // doubles contact damage
        assertEquals(
            "a doubled hit doubles the wake behind it; it does not add twenty to it",
            base * 2f, asteroid.getTrailDamage(), 0.0001f
        )
    }

    @Test
    fun `the ramp keeps a small fragment's wake smaller than a large one's`() {
        val small = Asteroid().apply {
            initialize(0f, 0f, AsteroidSize.SMALL, AsteroidType.TRAIL)
            damageBonus = SpawnSystem.DAMAGE_RAMP_MAX_BONUS
        }
        val large = Asteroid().apply {
            initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.TRAIL)
            damageBonus = SpawnSystem.DAMAGE_RAMP_MAX_BONUS
        }
        assertTrue(
            "the player reads the field by size — the ramp must not flatten that",
            large.getTrailDamage() > small.getTrailDamage() * 2f
        )
    }

    @Test
    fun `a sixteen minute run is taking real damage`() {
        // The design target: 16 minutes should be hard. A fully upgraded hull is 50 base health,
        // 50 from Salvage Plate and 50 of shield, and the invulnerability window allows one contact
        // hit every 1.5s — so anything under about 60 a touch loses to a maxed Vampiric Core's
        // 15 HP/s and the run cannot end.
        val contact = Asteroid.BASE_CONTACT_DAMAGE +
            SpawnSystem.asteroidDamageBonus(16f * 60f, astroLoopMode = true)
        assertTrue("16 minutes must out-damage full healing, got $contact", contact >= 60f)
    }

    @Test
    fun `a pooled asteroid does not carry a stale bonus into the next run`() {
        EntityPools.asteroids.freeAll()
        val first = EntityPools.asteroids.obtain()
        first.initialize(0f, 0f, AsteroidSize.LARGE, AsteroidType.ROCK)
        first.damageBonus = 40f
        EntityPools.asteroids.freeAll()

        val reused = EntityPools.asteroids.obtain()
        assertEquals("reset must clear the ramp, or run two starts at minute forty",
            0f, reused.damageBonus, 0.0001f)
    }
}
