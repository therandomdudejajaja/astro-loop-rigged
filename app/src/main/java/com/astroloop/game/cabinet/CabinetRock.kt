package com.astroloop.game.cabinet

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rock sizes and their scores.
 *
 * [points] is the classic inversion — smaller is worth more, because smaller is harder
 * to hit. A fully cleared large rock is 7 destructions worth 17 points, which is what
 * puts 999 in wave 8 at the six-minute target.
 */
enum class RockSize(val points: Int) {
    LARGE(1),
    MEDIUM(2),
    SMALL(3);

    val next: RockSize?
        get() = when (this) {
            LARGE -> MEDIUM
            MEDIUM -> SMALL
            SMALL -> null
        }
}

/**
 * A rock. [shape] is a unit-radius closed polygon in local space, scaled at draw time
 * by [radius]; the generator is the main game's Asteroid.generateShape ROCK branch,
 * reproduced here so the cabinet has no dependency on the entity package.
 */
class CabinetRock(
    val size: RockSize,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val shape: FloatArray,
    var rot: Float,
    val spin: Float
) {
    fun radius(m: CabinetMetrics): Float = m.rockRadius(size)

    fun update(dt: Float, m: CabinetMetrics) {
        x += vx * dt
        y += vy * dt
        rot += spin * dt
        if (x < 0f) x += m.width
        if (x >= m.width) x -= m.width
        if (y < 0f) y += m.height
        if (y >= m.height) y -= m.height
    }

    companion object {
        /**
         * 7-11 vertices at 0.3 radial variance — the same parameters as
         * `Asteroid.generateShape()`'s ROCK branch (`Asteroid.kt:128-145`). Returns
         * interleaved x,y pairs on a unit radius.
         */
        fun generateShape(rng: Random): FloatArray {
            val n = rng.nextInt(7, 12)
            val out = FloatArray(n * 2)
            val variance = 0.3f
            for (i in 0 until n) {
                val a = (i.toFloat() / n) * 2f * PI.toFloat()
                val r = 1f - variance + rng.nextFloat() * variance * 2f
                out[i * 2] = cos(a) * r
                out[i * 2 + 1] = sin(a) * r
            }
            return out
        }

        /** Two fragments, or none if [rock] is already SMALL. */
        fun split(rock: CabinetRock, rng: Random, m: CabinetMetrics): List<CabinetRock> {
            val child = rock.size.next ?: return emptyList()
            val speed = m.rockBaseSpeed * (1.3f + rng.nextFloat() * 0.5f)
            return List(2) {
                val a = rng.nextFloat() * 2f * PI.toFloat()
                CabinetRock(
                    child, rock.x, rock.y,
                    rock.vx + cos(a) * speed,
                    rock.vy + sin(a) * speed,
                    generateShape(rng),
                    rng.nextFloat() * 2f * PI.toFloat(),
                    (rng.nextFloat() - 0.5f) * 1.4f
                )
            }
        }
    }
}
