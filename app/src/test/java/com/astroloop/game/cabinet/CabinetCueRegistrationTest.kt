package com.astroloop.game.cabinet

import com.astroloop.game.core.SoundManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Every cue the cabinet can emit must be a sound `SoundManager` actually loads.
 *
 * ⚠️ THIS TEST EXISTS BECAUSE ITS ABSENCE SHIPPED A SILENT FEATURE. The crystal's three
 * cues were authored, baked into `res/raw`, wired through [CabinetCues] and covered by
 * thirty-four tests — and none of it made a sound, because the ids were never added to
 * `SoundManager.allSfxEventIds`. `playSFX` does `sfxIds[eventId] ?: return`, and `sfxIds`
 * is populated from that list alone, so an unregistered id is a silent no-op with no crash
 * and no log line.
 *
 * The existing tests could not catch it: they assert the id strings against themselves.
 * This one crosses the seam to the registry, which is the only place the two halves meet.
 */
class CabinetCueRegistrationTest {

    /** Every id [CabinetCues] can produce, gathered by driving it rather than by listing. */
    private fun everyCueId(): Set<String> {
        val out = ArrayList<CabinetCues.Cue>()
        val frames = listOf(
            CabinetCues.Frame(shotsFired = 1),
            CabinetCues.Frame(destroyedLarge = 1),
            CabinetCues.Frame(destroyedMedium = 1),
            CabinetCues.Frame(destroyedSmall = 1),
            CabinetCues.Frame(shipDied = true),
            CabinetCues.Frame(heartbeat = true, heartbeatHigh = false),
            CabinetCues.Frame(heartbeat = true, heartbeatHigh = true),
            CabinetCues.Frame(bulletsEmitted = 1),
            CabinetCues.Frame(crystalHits = 1),
            CabinetCues.Frame(shattered = true)
        )
        frames.forEach { CabinetCues.forFrame(it, out) }
        return out.map { it.eventId }.toSet()
    }

    @Test fun everyCueIsRegisteredWithSoundManager() {
        val missing = everyCueId() - SoundManager.allSfxEventIds.toSet()
        assertEquals("cues that would play nothing at all: $missing", emptySet<String>(), missing)
    }

    @Test fun theCabinetCanActuallyEmitEveryBeltSoundTheRegistryCarries() {
        // The other direction: a registered id nothing can emit is a sample shipped for
        // nothing. sfx_belt_coin is the deliberate exception — it fires from a touch
        // handler, not from a frame, so CabinetCues never produces it.
        val registered = SoundManager.allSfxEventIds.filter { it.startsWith("sfx_belt_") }.toSet()
        val orphans = registered - everyCueId() - setOf("sfx_belt_coin")
        assertEquals("registered but unreachable: $orphans", emptySet<String>(), orphans)
    }
}
