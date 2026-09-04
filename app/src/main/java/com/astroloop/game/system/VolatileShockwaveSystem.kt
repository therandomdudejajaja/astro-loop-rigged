package com.astroloop.game.system

import com.astroloop.game.core.GameState
import com.astroloop.game.entity.Asteroid
import com.astroloop.game.entity.EnemyShip
import com.astroloop.game.entity.Entity
import com.astroloop.game.entity.Ship
import com.astroloop.game.entity.VisualEffectManager
import kotlin.math.sqrt

/**
 * Expanding volatile-asteroid detonation fronts.
 *
 * The detonation used to apply its full-radius damage on the frame it fired, while
 * `VisualEffectManager.addExplosion` expanded the ring from nothing over 0.4s. Players were being
 * killed by a shockwave that had visibly not reached them, and could not learn to dodge something
 * whose hitbox did not match its picture.
 *
 * The front now sweeps at the visual's own rate and damages the annulus it crossed this frame —
 * the same technique the Phoenix Core shockwave uses in GameSurfaceView. Total damage is unchanged;
 * only its arrival moved.
 *
 * **Damage is flat — no crit, no momentum. Owner's decision, 2026-08-31; do not "restore" it.**
 * The instant explosion this replaced ran its damage through `applyDamageModifiers`, so Lucky
 * Rounds' crit and Momentum Drive's speed bonus applied to it, and a fully invested build made a
 * volatile blast hit about 75% harder. That was a quirk of routing an environmental hazard through
 * the player's weapon pipeline: a rock detonating is not a gun they fired. The consequence is real
 * and intended — volatile chain-clearing is weaker than it was in 1.2.
 */
class VolatileShockwaveSystem(
    private val state: GameState,
    private val visualEffects: VisualEffectManager,
    private val onAsteroidDestroyed: (Asteroid) -> Unit,
    private val onEnemyDestroyed: (EnemyShip) -> Unit,
    private val onShipDamaged: (Float) -> Unit,
) {

    companion object {
        /**
         * The detonation's own colour, matching what `LootSystem` gives the fuse projectile.
         *
         * Damage numbers are coloured by their source everywhere else in the game, so a volatile
         * blast's numbers have to be too — otherwise the one AoE that kills without explaining
         * itself is the environmental one.
         */
        const val DETONATION_COLOR = 0xFFFF4400.toInt()

        /**
         * Seconds the front takes to reach [Wave.maxRadius].
         *
         * Matched to `VisualEffectManager.addExplosion`'s `lifetime` so the hitbox and the ring
         * finish together. If that lifetime ever changes, this must follow it.
         */
        const val SWEEP_SECONDS = 0.4f
    }

    /** One detonation front. [prevRadius]..[radius] is the annulus it crossed this frame. */
    class Wave(
        val x: Float,
        val y: Float,
        val maxRadius: Float,
        val damage: Float,
    ) {
        var prevRadius = 0f
        var radius = 0f
        /** The ship can travel with the front, so it needs an explicit once-only latch. */
        var hitShip = false
        /**
         * Asteroids/enemies already damaged by this wave. A target flung outward by a gravity
         * well or repulsion effect can otherwise sit inside several consecutive per-frame
         * annuli and take this wave's damage more than once — the ship has [hitShip] for the
         * same reason.
         *
         * Scoped to the Wave instance, not the system: it needs no explicit clearing, because
         * once a wave finishes sweeping and [update] drops it from the wave list, this set (and
         * every reference it holds) becomes unreachable garbage along with the Wave itself.
         *
         * This is safe against entity pooling too. Entities are identity-keyed here, and a
         * pooled Asteroid/EnemyShip that dies and gets recycled into an unrelated new entity
         * *within this same wave's ~0.4s lifetime* could in principle inherit a stale
         * "already hit" entry. But that only mutes this one wave's already-finishing sweep on
         * the recycled slot for whatever remains of that 0.4s — it can never reach a later
         * wave, because each wave owns its own set that dies with it.
         */
        val hitEntities = HashSet<Entity>()
    }

    private val waves = mutableListOf<Wave>()

    fun spawn(x: Float, y: Float, maxRadius: Float, damage: Float) {
        if (maxRadius <= 0f) return
        waves.add(Wave(x, y, maxRadius, damage))
    }

    fun activeCount(): Int = waves.size

    /**
     * @param crystalImmune Crystal powers, which cannot change mid-frame — so a snapshot is
     *   honest for it. The ship's own invulnerability is deliberately NOT a parameter: it is
     *   read live, per wave, because a wave that lands grants i-frames and every wave after it
     *   in this same call has to see them. Passed as a snapshot, a chain of simultaneous
     *   detonations all read the value from before the first one hit.
     */
    fun update(
        deltaTime: Float,
        ship: Ship,
        asteroids: List<Asteroid>,
        enemies: List<EnemyShip>,
        crystalImmune: Boolean
    ) {
        if (waves.isEmpty()) return

        val iter = waves.iterator()
        while (iter.hasNext()) {
            val wave = iter.next()
            wave.prevRadius = wave.radius
            wave.radius += (wave.maxRadius / SWEEP_SECONDS) * deltaTime

            for (asteroid in asteroids) {
                if (!asteroid.isActive || wave.hitEntities.contains(asteroid)) continue
                val d = distance(wave, asteroid.position.x, asteroid.position.y)
                if (d > wave.prevRadius && d <= wave.radius) {
                    wave.hitEntities.add(asteroid)
                    recordDamage(wave.damage)
                    visualEffects.addDamageNumber(
                        asteroid.position.x,
                        asteroid.position.y - asteroid.radius,
                        wave.damage.toInt(),
                        DETONATION_COLOR
                    )
                    if (asteroid.takeDamage(wave.damage)) onAsteroidDestroyed(asteroid)
                }
            }

            // Mirrors the asteroid path above — weapon parity. Non-crewmate enemies also keep
            // the old processExplosionOnDespawn's off-screen guard: the wave's origin can sit
            // right at the edge of what the camera shows, and without it an enemy just past
            // that edge could take (and be killed by) a blast the player never saw land.
            for (enemy in enemies) {
                if (!enemy.isActive || wave.hitEntities.contains(enemy)) continue
                if (!enemy.isCrewmate && !isOnScreen(ship, enemy)) continue
                val d = distance(wave, enemy.position.x, enemy.position.y)
                if (d > wave.prevRadius && d <= wave.radius) {
                    wave.hitEntities.add(enemy)
                    recordDamage(wave.damage)
                    // Same suppression the old explosion path used: a number over a shielded or
                    // perfectly-dodging enemy would report damage that never landed.
                    if (!enemy.perfectDodge && !enemy.isSpawnShielded) {
                        visualEffects.addDamageNumber(
                            enemy.position.x,
                            enemy.position.y - enemy.radius,
                            wave.damage.toInt(),
                            DETONATION_COLOR
                        )
                    }
                    if (enemy.takeDamage(wave.damage)) onEnemyDestroyed(enemy)
                }
            }

            // ship.isInvulnerable re-read here, not snapshotted by the caller: the callback
            // below grants i-frames, and the next wave in this very loop must respect them.
            // Without that, N simultaneous detonations dealt N x full damage in one instant —
            // at the Astro Loop ramp cap that is 180 apiece, and the sharpest death spike in
            // the build. Every other damage source in the game grants i-frames; this now does
            // too. The hitShip latch stays as well, for the case i-frames lapse while a front
            // is still sweeping.
            if (!crystalImmune && !ship.isInvulnerable && !wave.hitShip) {
                val d = distance(wave, ship.position.x, ship.position.y) - ship.radius
                if (d <= wave.radius) {
                    wave.hitShip = true
                    onShipDamaged(wave.damage)
                }
            }

            if (wave.radius >= wave.maxRadius) iter.remove()
        }
    }

    fun reset() {
        waves.clear()
    }

    /**
     * Book this damage to the telemetry page.
     *
     * The instant explosion this system replaced did the same. Without it
     * `telemetryDamageByWeapon["volatile_detonation"]` reads zero for the whole release, and the
     * balance pass reads that as "volatile asteroids stopped mattering" rather than "nobody wrote
     * the number down".
     */
    private fun recordDamage(amount: Float) {
        state.telemetryDamageByWeapon["volatile_detonation"] =
            (state.telemetryDamageByWeapon["volatile_detonation"] ?: 0f) + amount
        state.telemetryTotalDamageDealt += amount
    }

    private fun distance(wave: Wave, x: Float, y: Float): Float {
        val dx = x - wave.x
        val dy = y - wave.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun isOnScreen(ship: Ship, entity: Entity): Boolean {
        val margin = 50f
        val cameraX = ship.position.x - state.screenWidth / 2f
        val cameraY = ship.position.y - state.screenHeight / 2f
        return entity.position.x >= cameraX - margin &&
               entity.position.x <= cameraX + state.screenWidth + margin &&
               entity.position.y >= cameraY - margin &&
               entity.position.y <= cameraY + state.screenHeight + margin
    }
}
