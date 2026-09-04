package com.astroloop.game.core

import com.astroloop.game.data.WeaponDefinitions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * An evolution takes the place of the weapon it grew from — in the grid, and in the drop table.
 *
 * weaponLevels is a linkedMapOf because HUDRenderer draws its keys in order. Removing the base and
 * appending the evolution therefore moved the weapon to the end of the grid mid-run. And with the
 * base key gone, getWeaponLevel reported zero, so the drop table happily offered the weapon the
 * player had just evolved past.
 */
class EvolutionSlotTest {

    private lateinit var state: GameState

    @Before
    fun setup() {
        state = GameState()
    }

    @Test
    fun `the evolution occupies the base weapon's position`() {
        state.weaponLevels["pulse_cannon"] = 5
        state.weaponLevels["railgun"] = 3
        state.weaponLevels["needle_gun"] = 2

        state.replaceWeapon("railgun", "oblivion_beam", GameConfig.WEAPON_MAX_LEVEL)

        assertEquals(
            listOf("pulse_cannon", "oblivion_beam", "needle_gun"),
            state.weaponLevels.keys.toList()
        )
        assertEquals(GameConfig.WEAPON_MAX_LEVEL, state.weaponLevels["oblivion_beam"])
        assertFalse("the base weapon is gone", state.weaponLevels.containsKey("railgun"))
    }

    @Test
    fun `replacing a weapon that is not held appends it`() {
        state.weaponLevels["pulse_cannon"] = 5

        state.replaceWeapon("railgun", "oblivion_beam", GameConfig.WEAPON_MAX_LEVEL)

        assertEquals(listOf("pulse_cannon", "oblivion_beam"), state.weaponLevels.keys.toList())
    }

    @Test
    fun `a base weapon reports its evolution once owned`() {
        val def = WeaponDefinitions.getWeaponDef("needle_gun")
        assertNotNull("needle_gun must define an evolution", def?.evolutionWeaponId)

        assertFalse(state.hasEvolutionOf("needle_gun"))
        state.addEvolution(def!!.evolutionWeaponId!!)
        assertTrue(state.hasEvolutionOf("needle_gun"))
    }

    @Test
    fun `an unrelated weapon is unaffected`() {
        state.addEvolution("siphon_needles")
        assertFalse("evolving needles must not retire the railgun",
            state.hasEvolutionOf("railgun"))
    }

    @Test
    fun `a weapon with no evolution never reports one`() {
        assertFalse(state.hasEvolutionOf("not_a_weapon"))
    }
}
