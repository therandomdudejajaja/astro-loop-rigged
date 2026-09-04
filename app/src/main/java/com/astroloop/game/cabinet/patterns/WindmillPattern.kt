package com.astroloop.game.cabinet.patterns

import com.astroloop.game.cabinet.CabinetBullet
import com.astroloop.game.cabinet.CabinetCrystal
import com.astroloop.game.cabinet.CabinetMetrics
import com.astroloop.game.cabinet.ReckoningFairness
import com.astroloop.game.cabinet.ReckoningPattern
import com.astroloop.game.cabinet.crossingLife
import com.astroloop.game.cabinet.crystalX
import com.astroloop.game.cabinet.crystalY
import com.astroloop.game.cabinet.forEachTick
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * **WINDMILL** — three slow arms sweeping, widely spaced.
 *
 * *Can you time a dive through a rotating gap?* Third in the teaching order: PULSE asks
 * for one commitment, VOLLEY for constant movement, this for sustained weaving.
 *
 * **This pattern could not have existed in the fight it replaces.** `CrystalFightSystem`
 * carried a hard floor in its own comments — *"Speeds NEVER drop below 375 × 1.3 = 487.5
 * px/s (max player speed × margin) or the fight becomes winnable by flying away."* On a
 * wrapping playfield you cannot fly away; fleeing returns you into what you fled. That
 * rule is void, and slow readable threats are what it bought.
 *
 * **Escalation spins the arms faster** rather than adding more of them. `tighten` divides
 * the angular rate, so a lap closes the sector you have to cross and the picket spacing
 * along each arm together.
 */
class WindmillPattern : ReckoningPattern {

    override val name = "WINDMILL"
    override val duration = 15f

    override fun minSpacing(m: CabinetMetrics, tighten: Float): Float =
        min(alongArmSpacing(m, tighten), sectorWidth(m))

    /** Picket spacing along one arm: how far a bullet gets before the next is emitted. */
    private fun alongArmSpacing(m: CabinetMetrics, tighten: Float): Float =
        speed(m) * EMIT_INTERVAL * tighten

    /** The gap between two arms at engagement range. 904.8px — never the binding term. */
    private fun sectorWidth(m: CabinetMetrics): Float =
        2f * PI.toFloat() * ReckoningFairness.engagementRange(m) / ARMS

        override fun peakDensity(m: CabinetMetrics, tighten: Float): Float =
        ARMS * ceil(crossTime(m) / (EMIT_INTERVAL * tighten))

    override fun emit(
        t: Float, dt: Float, tighten: Float, m: CabinetMetrics, shipX: Float, shipY: Float
    ): List<CabinetBullet> {
        val out = ArrayList<CabinetBullet>()
        forEachTick(t, dt, EMIT_INTERVAL * tighten) { _, at ->
            val cx = crystalX(m)
            val cy = crystalY(m)
            val r0 = CabinetCrystal.RADIUS_FRAC * m.minEdge
            val sp = speed(m)
            val life = crossingLife(m, sp)
            // Angle derives from the emission's own scheduled instant, not from an
            // accumulated field — that is what keeps the pattern stateless, so a jump
            // straight into this phase needs no warm-up.
            val base = omega(tighten) * at
            val step = 2f * PI.toFloat() / ARMS
            for (i in 0 until ARMS) {
                val a = base + i * step
                val ca = cos(a)
                val sa = sin(a)
                out.add(
                    CabinetBullet(cx + ca * r0, cy + sa * r0, ca * sp, sa * sp, life, hostile = true)
                )
            }
        }
        return out
    }

    /** Faster as the fight tightens — dividing is what makes a lap harder to cross. */
    private fun omega(tighten: Float): Float = OMEGA / tighten

    private fun speed(m: CabinetMetrics): Float = SPEED_FRAC * m.minEdge

    private fun crossTime(m: CabinetMetrics): Float {
        val corner = hypot(m.width / 2f, m.height / 2f)
        return (corner - CabinetCrystal.RADIUS_FRAC * m.minEdge) / speed(m)
    }

    companion object {
        /**
         * 280.8 px/s at reference — below the old fight's 487.5 px/s floor, and the
         * clearest single piece of evidence that moving into the cabinet bought
         * something rather than just relocating the fight.
         */
        const val SPEED_FRAC = 0.26f

        /** 174.1px along an arm at lap 1; 73.4px by lap 4, under the floor. */
        const val EMIT_INTERVAL = 0.62f

        const val ARMS = 3

        /** rad/s at lap 1. A full sweep takes 11.4s — slow enough to plan against. */
        const val OMEGA = 0.55f

        /**
         * Radians an arm must turn to sweep the danger diameter at engagement range:
         * `dangerDiameter / engagementRange` = 35.64 / 432. Constant because both terms
         * are fractions of `minEdge`, so the ratio is resolution-independent.
         */
        const val DANGER_SWEEP_REF = 0.0825f
    }
}
