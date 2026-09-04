package com.astroloop.game.cabinet

import kotlin.math.hypot
import kotlin.random.Random

/**
 * The demo the machine plays to itself: a [CabinetSim] on a fixed seed, flown by a dumb
 * autopilot, restarting on a beat when it dies.
 *
 * There are two places a BELT RUN attract loop runs — behind the cabinet's own menu and
 * scores screens, and on the store page's bezel — and this class is the whole of it, so
 * they cannot drift apart. They did: the bezel had its own two-line version that restarted
 * in the same frame it died (wiping the wreck it had drawn once) and hardcoded a zero
 * input vector, so its ship never steered at all.
 *
 * Pure — no Canvas, no Android. Its deaths are never recorded and its score is never
 * anyone's; the host is responsible for keeping it off the real score board.
 */
class CabinetAttract(
    private val m: CabinetMetrics,
    seed: Int = ATTRACT_SEED
) {
    val sim = CabinetSim(m, Random(seed))

    /** Time spent sitting on the demo's wreck before it starts over. */
    private var restartHold = 0f

    init { sim.start() }

    fun update(dt: Float) {
        // Loop forever: a cabinet showing GAME OVER to an empty room is a cabinet nobody
        // walks up to. But restart on a BEAT, not in the same frame it died — the demo's
        // own wreck drifting and fading is the punctuation, and cutting it made the loop
        // read as a glitch.
        if (sim.over) {
            restartHold += dt
            // Both conditions matter: the hold is only a minimum beat, and
            // debris.isEmpty() is what actually guarantees the wreck was seen before
            // start() wipes it. A large dt (e.g. resuming from background) can blow past
            // the hold in one step; gating on the wreck too makes that dt-proof instead
            // of relying on ATTRACT_RESTART_HOLD staying comfortably ahead of
            // CabinetDebris.LIFETIME.
            if (restartHold >= ATTRACT_RESTART_HOLD && sim.debris.isEmpty()) {
                sim.start()
                restartHold = 0f
            }
        } else {
            restartHold = 0f
        }
        val (ax, ay) = aim()
        sim.update(dt, ax, ay, true)
    }

    /**
     * The autopilot: steer at the nearest rock. Deliberately dumb — from across the
     * room it reads as competent play, and it dies often enough to look human.
     */
    private fun aim(): Pair<Float, Float> {
        val ship = sim.ship
        // Toroidal, like everything else: otherwise the demo ignores a rock adjacent
        // across the seam and flies the long way round to a "nearer" one.
        val target = sim.rocks.minByOrNull {
            m.distance(it.x, it.y, ship.x, ship.y)
        } ?: return 0f to 0f
        val dx = m.wrappedDelta(target.x, ship.x, m.width)
        val dy = m.wrappedDelta(target.y, ship.y, m.height)
        val d = hypot(dx, dy)
        if (d < 0.001f) return 0f to 0f
        return (dx / d * ATTRACT_THRUST) to (dy / d * ATTRACT_THRUST)
    }

    companion object {
        /** Fixed so the demo is the same every time the store page is opened. */
        const val ATTRACT_SEED = 20260814
        const val ATTRACT_THRUST = 0.7f

        /**
         * The *minimum* beat the demo holds on its wreck before restarting — not a
         * guarantee by itself. The restart is actually gated on this **and** on
         * `sim.debris.isEmpty()`; the wreck's own lifetime is the other half of the
         * condition. Under a normal per-frame dt this constant is comfortably longer
         * than `CabinetDebris.LIFETIME`, so the debris check rarely ends up doing the
         * work — but a single large dt (e.g. resuming from background) can jump straight
         * past this value while wreckage is still fading, and the debris check is what
         * stops the restart from popping it away mid-fade.
         */
        const val ATTRACT_RESTART_HOLD = 1.4f
    }
}
