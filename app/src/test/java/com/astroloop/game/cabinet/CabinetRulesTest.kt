package com.astroloop.game.cabinet

import com.astroloop.game.data.PilotDefinitions
import org.junit.Assert.*
import org.junit.Test

class CabinetRulesTest {

    @Test fun everyPilotHasARealEntryNotAFallthrough() {
        // forPilot returns a non-nullable type, so assertNotNull on it can never fail
        // and would not catch the failure this guards: an id typo'd in the map,
        // silently resolving to CabinetRules.NONE and putting a blank on the board.
        for (p in PilotDefinitions.pilots) {
            assertTrue(
                "${p.callsign} (${p.id}) has no entry - forPilot would fall through to NONE",
                p.id in PilotCabinetRules.mappedPilotIds
            )
        }
    }

    @Test fun anUnknownPilotFallsThroughToNone() {
        // Pins the fallback that makes the test above necessary.
        assertEquals(CabinetRules.NONE, PilotCabinetRules.forPilot("pilot_nonexistent"))
    }

    @Test fun initialsAreTheConsonantSqueeze() {
        assertEquals("MDC", PilotCabinetRules.forPilot("pilot_medic").initials)
        assertEquals("RSC", PilotCabinetRules.forPilot("pilot_rascal").initials)
        assertEquals("BRT", PilotCabinetRules.forPilot("pilot_brutus").initials)
        assertEquals("FRS", PilotCabinetRules.forPilot("pilot_frost").initials)
        assertEquals("DSH", PilotCabinetRules.forPilot("pilot_dash").initials)
        assertEquals("MBR", PilotCabinetRules.forPilot("pilot_ember").initials)
        assertEquals("FNG", PilotCabinetRules.forPilot("pilot_fang").initials)
        assertEquals("KRK", PilotCabinetRules.forPilot("pilot_kraken").initials)
        assertEquals("WSK", PilotCabinetRules.forPilot("pilot_whiskers").initials)
        assertEquals("UN7", PilotCabinetRules.forPilot("pilot_unit7").initials)
        assertEquals("HVC", PilotCabinetRules.forPilot("pilot_havoc").initials)
        // Astro is the one exception to the squeeze: his is the only name on the board
        // spelled the way it sounds, and he is the pilot you fly the ending as.
        assertEquals("AST", PilotCabinetRules.forPilot("pilot_astro").initials)
    }

    @Test fun initialsAreAllDistinct() {
        // The board is a ranking of people. Two pilots sharing a row label would make
        // gate progress unreadable.
        val all = PilotDefinitions.pilots.map { PilotCabinetRules.forPilot(it.id).initials }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun initialsAreAlwaysThreeCharacters() {
        // The board columns are laid out monospaced against exactly three.
        for (p in PilotDefinitions.pilots) {
            val i = PilotCabinetRules.forPilot(p.id).initials
            assertEquals("${p.callsign} initials '$i'", 3, i.length)
        }
    }
}
