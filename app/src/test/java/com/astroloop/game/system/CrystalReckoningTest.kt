package com.astroloop.game.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrystalReckoningTest {

    @Test
    fun theGateIsShutUntilEveryPilotHasClearedTheMachine() {
        assertFalse(CrystalReckoning.shouldEnter(allPilotsCleared = false, crystalReleased = false))
    }

    @Test
    fun theGateOpensWhenAllTwelveHaveCleared() {
        assertTrue(CrystalReckoning.shouldEnter(allPilotsCleared = true, crystalReleased = false))
    }

    @Test
    fun aReleasedCrystalShutsTheGateForGood() {
        assertFalse(CrystalReckoning.shouldEnter(allPilotsCleared = true, crystalReleased = true))
    }

    @Test
    fun releaseDominatesAnIncompleteBoardToo() {
        assertFalse(CrystalReckoning.shouldEnter(allPilotsCleared = false, crystalReleased = true))
    }
}
