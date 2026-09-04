package com.astroloop.game.core

import org.junit.Assert.*
import org.junit.Test

/**
 * The SFX coalescer must collapse a volley without thinning a cadence.
 *
 * It failed the second half. The window was 40ms and the heart-to-heart typewriter ticks once
 * per 0.04s — the same number. The accumulator driving the typewriter resets to zero on each tick
 * (discarding the overshoot), so consecutive ticks are exactly 40ms apart whenever the frame
 * period divides 40, which it does at the game's 8ms budget. A `< 40ms` gate against a 40ms
 * cadence has a margin of zero, and the timestamps it compares are sampled inside `update()`,
 * drifting sub-millisecond with per-frame work. Ticks measured 39 and vanished.
 *
 * These tests drive the gate directly rather than through `playSFX`, because nothing about the
 * defect involves a SoundPool — it is arithmetic on two timestamps.
 */
class SfxCoalesceTest {

    /** A distinct id per test: the coalescer's map is object-scoped and there is no reset hook. */
    private var seq = 0
    private fun freshId() = "sfx_test_coalesce_${seq++}_${System.nanoTime()}"

    @Test
    fun `a volley in one frame collapses to a single play`() {
        val id = freshId()
        val now = 100_000L

        assertFalse("the first of the volley must play", SoundManager.isCoalescedDuplicate(id, now))
        // Nine more enemies firing the same weapon in the same update(), spread across the couple
        // of milliseconds a heavy frame takes to walk its enemy list.
        var suppressed = 0
        for (i in 1..9) {
            if (SoundManager.isCoalescedDuplicate(id, now + i / 4)) suppressed++
        }
        assertEquals("the other nine voices must be dropped", 9, suppressed)
    }

    @Test
    fun `the typewriter's cadence is not thinned`() {
        val id = freshId()
        // One tick per 40ms, jittered by the sub-millisecond drift in where inside a frame
        // update() reaches the tick. This is the exact shape that was being swallowed.
        val rng = java.util.Random(7)
        var trueTime = 500_000L
        var dropped = 0
        var played = 0

        repeat(300) {
            trueTime += SoundManager.FASTEST_INTENTIONAL_REPEAT_MS
            val sampled = trueTime + (rng.nextInt(3) - 1)   // -1, 0 or +1 ms
            if (SoundManager.isCoalescedDuplicate(id, sampled)) dropped++ else played++
        }

        assertEquals("every typewriter tick must reach the pool", 0, dropped)
        assertEquals("and the test must actually have ticked", 300, played)
    }

    @Test
    fun `the window keeps real headroom under the fastest intentional repeat`() {
        // The structural guard behind the test above: it is not enough for the window to be
        // *below* the fastest cadence, because the comparison is made on jittering wall-clock
        // samples. Half the cadence is the margin this codebase commits to.
        assertTrue(
            "SFX_COALESCE_MS (${SoundManager.SFX_COALESCE_MS}ms) must leave margin under the " +
                "${SoundManager.FASTEST_INTENTIONAL_REPEAT_MS}ms typewriter cadence",
            SoundManager.SFX_COALESCE_MS <= SoundManager.FASTEST_INTENTIONAL_REPEAT_MS / 2
        )
    }

    @Test
    fun `the window still covers a frame at the game's budget`() {
        // The other side of the bound: shrinking it until it no longer spans a frame would let a
        // volley back through, which is the whole point of the coalescer.
        assertTrue(
            "SFX_COALESCE_MS (${SoundManager.SFX_COALESCE_MS}ms) must cover a " +
                "${GameConfig.FRAME_TIME_MS}ms frame",
            SoundManager.SFX_COALESCE_MS >= GameConfig.FRAME_TIME_MS
        )
    }
}
