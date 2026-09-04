package com.astroloop.game.data

import org.junit.Assert.*
import org.junit.Test

/**
 * A bar conversation may not name a pilot who is not in it.
 *
 * Conversations are gated on their participants being recruited, so a line that name-drops a third
 * pilot escapes that gate entirely — which is how Unit-7 came to tell Medic about Astro to players
 * who had never met him, spoiling the last recruit.
 *
 * TB-26 and Tobar are exempt: he tends the bar, so he is present in every scene by definition.
 */
class BarConversationSpoilerTest {

    private val exempt = setOf("TB-26", "TOBAR")

    /** Callsign → pilot id, for every pilot the game can name. */
    private val callsigns: Map<String, String> =
        PilotDefinitions.pilots.associate { it.callsign.uppercase() to it.id }

    @Test
    fun `no line names a pilot who is not a participant`() {
        val offences = mutableListOf<String>()

        for (conversation in BarConversations.allConversations) {
            val present = conversation.participantIds.toSet()
            for (line in conversation.lines) {
                for ((callsign, pilotId) in callsigns) {
                    if (callsign in exempt) continue
                    if (!namesCallsign(line.text, callsign)) continue
                    if (pilotId in present) continue
                    offences += "[${conversation.participantIds}] ${line.speaker}: \"${line.text}\" names $callsign"
                }
            }
        }

        assertTrue(
            "a conversation may not name a pilot the player might not have recruited:\n" +
                offences.joinToString("\n"),
            offences.isEmpty()
        )
    }

    /** Whole-word, case-insensitive — so "Frost" matches but "frosted" does not. */
    private fun namesCallsign(text: String, callsign: String): Boolean =
        Regex("(?<![A-Za-z0-9-])${Regex.escape(callsign)}(?![A-Za-z0-9-])", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
}
