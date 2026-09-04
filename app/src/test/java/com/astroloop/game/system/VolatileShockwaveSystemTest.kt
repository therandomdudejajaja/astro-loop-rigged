package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.data.EnemyType
import com.astroloop.game.entity.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * A volatile detonation damages what its ring has actually reached.
 *
 * The old detonation applied full-radius damage on the frame it fired, while the visual expanded
 * from a dot over 0.4s — so players were killed by a shockwave that had visibly not arrived. The
 * front now sweeps, and damage lands as it passes.
 */
class VolatileShockwaveSystemTest {

    private lateinit var ship: Ship
    private lateinit var state: GameState
    private lateinit var system: VolatileShockwaveSystem
    private val destroyed = mutableListOf<Asteroid>()
    private val enemiesDestroyed = mutableListOf<EnemyShip>()
    private var shipDamage = 0f

    @Before
    fun setup() {
        EntityPools.asteroids.freeAll()
        EntityPools.enemies.freeAll()
        ship = Ship()
        ship.position.set(0f, 0f)
        state = GameState()
        destroyed.clear()
        enemiesDestroyed.clear()
        shipDamage = 0f
        system = VolatileShockwaveSystem(
            state = state,
            visualEffects = VisualEffectManager(),
            onAsteroidDestroyed = { destroyed.add(it) },
            onEnemyDestroyed = { enemiesDestroyed.add(it) },
            onShipDamaged = { shipDamage += it }
        )
    }

    @Test
    fun `a target beyond the front is not damaged on the first frame`() {
        // Ship sits at 200; the front covers 300px over 0.4s, so ~7.5px in the first 10ms.
        ship.position.set(200f, 0f)
        system.spawn(0f, 0f, maxRadius = 300f, damage = 30f)

        system.update(0.01f, ship, emptyList(), emptyList(), crystalImmune = false)

        assertEquals("the front has not reached the ship yet", 0f, shipDamage, 0.0001f)
    }

    @Test
    fun `the target is damaged once the front passes it`() {
        ship.position.set(200f, 0f)
        system.spawn(0f, 0f, maxRadius = 300f, damage = 30f)

        // Run the full sweep in small steps.
        repeat(50) { system.update(0.01f, ship, emptyList(), emptyList(), crystalImmune = false) }

        assertEquals("the passing front must deal its damage exactly once", 30f, shipDamage, 0.0001f)
    }

    @Test
    fun `a stationary target is never damaged twice by one wave`() {
        ship.position.set(50f, 0f)
        system.spawn(0f, 0f, maxRadius = 300f, damage = 30f)

        repeat(200) { system.update(0.005f, ship, emptyList(), emptyList(), crystalImmune = false) }

        assertEquals("one wave, one hit", 30f, shipDamage, 0.0001f)
    }

    @Test
    fun `an immune ship takes nothing`() {
        ship.position.set(50f, 0f)
        system.spawn(0f, 0f, maxRadius = 300f, damage = 30f)

        repeat(200) { system.update(0.005f, ship, emptyList(), emptyList(), crystalImmune = true) }

        assertEquals("crystal powers and i-frames block the wave", 0f, shipDamage, 0.0001f)
    }

    @Test
    fun `the wave expires once it reaches full radius`() {
        system.spawn(0f, 0f, maxRadius = 300f, damage = 30f)
        assertEquals(1, system.activeCount())

        repeat(100) { system.update(0.01f, ship, emptyList(), emptyList(), crystalImmune = false) }

        assertEquals("a finished wave must not linger", 0, system.activeCount())
    }

    @Test
    fun `an asteroid the front reaches takes damage and is reported when it dies`() {
        val asteroid = EntityPools.asteroids.obtain()
        asteroid.initialize(150f, 0f, AsteroidSize.SMALL, AsteroidType.ROCK)
        system.spawn(0f, 0f, maxRadius = 300f, damage = 9999f)

        repeat(100) { system.update(0.01f, ship, listOf(asteroid), emptyList(), crystalImmune = false) }

        assertEquals("the asteroid must be reported destroyed once", 1, destroyed.size)
        assertSame(asteroid, destroyed[0])
    }

    @Test
    fun `an enemy the front reaches is damaged and reported when it dies`() {
        // Enemies were damaged by the old instant detonation — the projectile is spawned with
        // isEnemyProjectile left false, so processExplosionOnDespawn's enemy block ran for it.
        // The swept front has to keep that, or volatile blasts silently stop hurting enemies.
        val enemy = EntityPools.enemies.obtain()
        enemy.initialize(150f, 0f, EnemyType.SCOUT, emptyList())
        // Enemies spawn under a 5s shield and takeDamage refuses while it is up — the old
        // instant detonation respected it too, so drop it rather than test a shielded target.
        enemy.spawnShieldTimer = 0f
        enemy.health = 1f

        system.spawn(0f, 0f, maxRadius = 300f, damage = 9999f)
        repeat(100) { system.update(0.01f, ship, emptyList(), listOf(enemy), crystalImmune = false) }

        assertEquals("the enemy must be reported destroyed exactly once", 1, enemiesDestroyed.size)
        assertSame(enemy, enemiesDestroyed[0])
    }

    @Test
    fun `an asteroid riding outward with the front is damaged only once`() {
        // The ship has an explicit latch because it can travel with the front. An asteroid flung
        // outward by a gravity well can do the same — sitting inside consecutive per-frame annuli
        // and taking one wave's damage several times over.
        val asteroid = EntityPools.asteroids.obtain()
        asteroid.initialize(10f, 0f, AsteroidSize.SMALL, AsteroidType.ROCK)
        asteroid.maxHealth = 10_000f
        asteroid.health = 10_000f

        system.spawn(0f, 0f, maxRadius = 300f, damage = 100f)
        // The front runs at 300/0.4 = 750 px/s, i.e. 3.75px per 5ms frame. Walk the asteroid
        // outward at 700 px/s from x=10: the front closes on it at 0.25px a frame, catches it
        // around frame 40, and then drags it along inside several consecutive annuli — which is
        // exactly the shape that produced repeat damage before the latch existed.
        repeat(80) {
            system.update(0.005f, ship, listOf(asteroid), emptyList(), crystalImmune = false)
            asteroid.position.x += 700f * 0.005f
        }

        assertEquals(
            "one wave, one hit — even for a target travelling with the front",
            10_000f - 100f, asteroid.health, 0.001f
        )
    }

    @Test
    fun `blast damage is booked to the telemetry page`() {
        // The instant explosion this system replaced recorded its damage. Without this the
        // telemetry page reads zero for volatile detonations across the whole release, and the
        // balance pass reads that as "volatile asteroids stopped mattering".
        val asteroid = EntityPools.asteroids.obtain()
        asteroid.initialize(150f, 0f, AsteroidSize.SMALL, AsteroidType.ROCK)
        asteroid.maxHealth = 10_000f
        asteroid.health = 10_000f

        system.spawn(0f, 0f, maxRadius = 300f, damage = 30f)
        repeat(100) { system.update(0.01f, ship, listOf(asteroid), emptyList(), crystalImmune = false) }

        assertEquals(30f, state.telemetryDamageByWeapon["volatile_detonation"] ?: 0f, 0.001f)
        assertEquals(30f, state.telemetryTotalDamageDealt, 0.001f)
    }

    @Test
    fun `overlapping waves cannot all hit in the same instant`() {
        // A chain of volatile asteroids detonating together used to deal every wave's damage at
        // once, with nothing between the hits — at the Astro Loop ramp cap that is several times
        // 180, and unsurvivable regardless of build. The callback grants i-frames here because
        // production's does; a fake that skipped that would test nothing.
        val iframed = VolatileShockwaveSystem(
            state = state,
            visualEffects = VisualEffectManager(),
            onAsteroidDestroyed = { },
            onEnemyDestroyed = { },
            onShipDamaged = { shipDamage += it; ship.makeInvulnerable() }
        )
        ship.position.set(50f, 0f)

        // Three detonations on top of each other, as a chain produces.
        repeat(3) { iframed.spawn(0f, 0f, maxRadius = 300f, damage = 100f) }
        repeat(200) { iframed.update(0.005f, ship, emptyList(), emptyList(), crystalImmune = false) }

        assertEquals("only the first front may land while the i-frames it granted hold",
            100f, shipDamage, 0.001f)
    }

    @Test
    fun `a ship already invulnerable takes nothing from a new wave`() {
        ship.position.set(50f, 0f)
        ship.makeInvulnerable()

        system.spawn(0f, 0f, maxRadius = 300f, damage = 30f)
        repeat(200) { system.update(0.005f, ship, emptyList(), emptyList(), crystalImmune = false) }

        assertEquals(0f, shipDamage, 0.001f)
    }
}
