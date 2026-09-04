package com.astroloop.game.hangar

import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first-launch swipe hint.
 *
 * A new player lands on the bar page with the intro cinematic running and nothing on screen
 * says the hangar has other rooms — the nav is hidden, its tap targets are gated off, and on
 * a phone no part of the next room bleeds in. TB-26 says where the launchpad is; Medic, who
 * cannot see who he is addressing, reacts to that rather than to the directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatSystemIntroHintTest {

    private lateinit var persistence: PersistenceManager
    private lateinit var state: HangarState
    private lateinit var chatSystem: ChatSystem

    @Before
    fun setup() {
        persistence = PersistenceManager(ApplicationProvider.getApplicationContext())
        persistence.resetAllProgress()
        state = HangarState(persistence)
        chatSystem = ChatSystem()
    }

    @Test
    fun `the hint names the launchpad and TB-26 is the one who says so`() {
        chatSystem.onIntroSwipeHint(state)
        drainConversation()

        assertEquals(3, state.chatMessages.size)
        assertEquals("TB-26", state.chatMessages[0].speaker)
        assertTrue(
            "the opening line must name the destination — got \"${state.chatMessages[0].text}\"",
            state.chatMessages[0].text.contains("next door", ignoreCase = true)
        )
    }

    @Test
    fun `Medic reacts in the middle, and to the listener rather than the directions`() {
        // TB-26 is the only character who may break the fourth wall, and the crew
        // must react when he does. Medic is the only pilot in the room at first launch, so
        // she is the only possible witness. Her confusion is about WHO he is talking to.
        chatSystem.onIntroSwipeHint(state)
        drainConversation()

        assertEquals("MEDIC", state.chatMessages[1].speaker)
        assertTrue(
            "Medic must react to the listener — got \"${state.chatMessages[1].text}\"",
            state.chatMessages[1].text.contains("nobody", ignoreCase = true)
        )
        assertEquals("TB-26 answers her", "TB-26", state.chatMessages[2].speaker)
    }

    @Test
    fun `Medic questions the word rather than using it`() {
        // "Commander" is TB-26's word alone. Medic quoting it back as the thing
        // she does not understand is the opposite of using it — so the property is that any
        // speaker who is not TB-26 may only ever put it in a question.
        chatSystem.onIntroSwipeHint(state)
        drainConversation()

        assertTrue(
            "vacuous otherwise: no non-TB-26 message contains \"commander\" at all — " +
                "got ${state.chatMessages.map { "${it.speaker}: ${it.text}" }}",
            state.chatMessages.any { it.speaker != "TB-26" && it.text.contains("commander", ignoreCase = true) }
        )

        for (m in state.chatMessages) {
            if (m.speaker == "TB-26") continue
            if (!m.text.contains("commander", ignoreCase = true)) continue
            assertTrue(
                "\"commander\" is TB-26's word alone; ${m.speaker} may only question it: \"${m.text}\"",
                m.text.contains("?")
            )
        }
    }

    @Test
    fun `every line fits its speaker's bar column budget`() {
        val budget = mapOf("TB-26" to 58, "MEDIC" to 58)
        chatSystem.onIntroSwipeHint(state)
        drainConversation()

        for (m in state.chatMessages) {
            assertTrue(
                "vacuous otherwise: \"${m.speaker}\" is not in the budget map, so this message " +
                    "would be skipped rather than measured",
                budget.containsKey(m.speaker)
            )
            val max = budget[m.speaker] ?: continue
            assertTrue(
                "${m.speaker} line over budget ($max): \"${m.text}\" is ${m.text.length}",
                m.text.length <= max
            )
        }
    }

    @Test
    fun `nothing unauthored speaks while the intro is running`() {
        // ⚠️ THE SETUP IS THE TEST. On a fresh save the idle paths are unreachable anyway —
        // the picker needs two unlocked pilots and shouldShowHints() needs a run behind you —
        // so asserting silence on a default state would pass with or without the guard and
        // prove nothing. Both conditions are therefore satisfied deliberately here, so that
        // without the guard this WOULD produce chatter. `chatterIsReachableWithoutTheGuard`
        // below is the control that keeps this honest.
        givenChatterWouldOtherwiseFire()
        state.introCinematic = true

        repeat(600) { chatSystem.update(0.1f, state) }

        assertTrue(
            "the intro must not produce idle chatter — got ${state.chatMessages.map { it.text }}",
            state.chatMessages.isEmpty()
        )
    }

    @Test
    fun `chatterIsReachableWithoutTheGuard`() {
        // The control for the test above. If this ever goes quiet, the silence there stops
        // meaning anything and the guard is no longer being exercised — fix this setup, do
        // not delete it.
        givenChatterWouldOtherwiseFire()
        state.introCinematic = false

        repeat(600) { chatSystem.update(0.1f, state) }

        assertTrue(
            "the guard test is vacuous unless the bar would otherwise speak here",
            state.chatMessages.isNotEmpty()
        )
    }

    /** Two pilots and a run behind them: enough for the idle paths to be live. */
    private fun givenChatterWouldOtherwiseFire() {
        persistence.unlockPilot("pilot_medic")
        persistence.unlockPilot("pilot_rascal")
        persistence.setRunsSincePilotUnlock(2)
        state = HangarState(persistence)
        state.currentPage = 0
        state.conversationCooldown = 0f
        state.activeConversation = null
    }

    @Test
    fun `the guard does not swallow the authored beat itself`() {
        // The guard sits BELOW the active-conversation delivery branch, so a queued beat is
        // still delivered while the intro runs. If it is ever moved above that branch, the
        // hint silences itself and this fails.
        state.introCinematic = true
        chatSystem.onIntroSwipeHint(state)
        drainConversation()

        assertEquals(3, state.chatMessages.size)
    }

    private fun drainConversation() {
        var guard = 0
        while (state.activeConversation != null && guard++ < 40) {
            chatSystem.update(ChatSystem.LINE_PAUSE + 0.1f, state)
        }
    }
}
