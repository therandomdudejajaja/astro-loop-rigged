package com.astroloop.game.entity

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * The boss's dodge is a flourish, not a physics response.
 *
 * takeDamage triggers an afterimage whenever the boss is invulnerable, and the afterimage teleports
 * it 60px. Solar Storm calls takeDamage directly — five times per volley at L5 — which shoved the
 * boss around the arena and straight through the sacrifice cutscene.
 */
class BossAfterimageTest {

    private lateinit var boss: Boss
    private lateinit var player: Ship

    @Before
    fun setup() {
        player = Ship()
        player.position.set(0f, 0f)
        boss = Boss()
        boss.initialize(500f, 500f, player)
        boss.isInvulnerable = true
    }

    @Test
    fun `the first dodge moves the boss`() {
        val startX = boss.position.x
        val startY = boss.position.y
        boss.takeDamage(10f)
        assertNotEquals("the dodge should still read as a dodge",
            startX to startY, boss.position.x to boss.position.y)
    }

    @Test
    fun `a volley cannot dodge the boss five times`() {
        boss.takeDamage(10f)
        val afterFirst = boss.position.x to boss.position.y

        // The rest of a Solar Storm volley, same frame.
        repeat(4) { boss.takeDamage(10f) }

        assertEquals("only the first strike of a volley may move the boss",
            afterFirst, boss.position.x to boss.position.y)
    }

    @Test
    fun `the dodge re-arms after its cooldown`() {
        boss.takeDamage(10f)
        val afterFirst = boss.position.x to boss.position.y

        boss.update(Boss.AFTERIMAGE_COOLDOWN + 0.01f)
        boss.takeDamage(10f)

        assertNotEquals("the dodge must come back",
            afterFirst, boss.position.x to boss.position.y)
    }
}
