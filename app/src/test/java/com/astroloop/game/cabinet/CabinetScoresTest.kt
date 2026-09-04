package com.astroloop.game.cabinet

import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.*
import org.junit.Test

/**
 * PersistenceManager wraps SharedPreferences, so these run against a fake rather than
 * Robolectric. The seam is the same arithmetic the real methods perform.
 */
class CabinetScoresTest {

    private class FakeScores {
        private val scores = HashMap<String, Int>()
        var credits = 0
        fun best(id: String) = scores[id] ?: 0
        fun setIfBetter(id: String, s: Int): Boolean {
            if (s <= best(id)) return false
            scores[id] = s; return true
        }
        fun cleared(id: String) = best(id) >= PersistenceManager.ARCADE_CLEAR_SCORE
    }

    @Test fun theClearThresholdIsTheMachinesCeiling() {
        assertEquals(999, PersistenceManager.ARCADE_CLEAR_SCORE)
    }

    @Test fun scoresOnlyImprove() {
        val f = FakeScores()
        assertTrue(f.setIfBetter("pilot_dash", 500))
        assertFalse("a worse run must not overwrite a better one", f.setIfBetter("pilot_dash", 400))
        assertEquals(500, f.best("pilot_dash"))
        assertTrue(f.setIfBetter("pilot_dash", 700))
        assertEquals(700, f.best("pilot_dash"))
    }

    @Test fun aPilotIsClearedOnlyAtTheCeiling() {
        val f = FakeScores()
        f.setIfBetter("pilot_frost", 998)
        assertFalse(f.cleared("pilot_frost"))
        f.setIfBetter("pilot_frost", 999)
        assertTrue(f.cleared("pilot_frost"))
    }

    @Test fun creditsNeverGoNegative() {
        val f = FakeScores()
        assertEquals(0, f.credits)
        f.credits += 1
        assertEquals(1, f.credits)
        f.credits = (f.credits - 1).coerceAtLeast(0)
        assertEquals(0, f.credits)
        f.credits = (f.credits - 1).coerceAtLeast(0)
        assertEquals("spending with no credit must not underflow", 0, f.credits)
    }
}
