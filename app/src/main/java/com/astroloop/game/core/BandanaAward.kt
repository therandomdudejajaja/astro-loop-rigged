package com.astroloop.game.core

import com.astroloop.game.data.PersistenceManager

/**
 * The bandana earn rule (finale chunk 1). Pure so it can be unit-tested apart
 * from the GameSurfaceView death path that invokes it.
 */
object BandanaAward {
    const val THRESHOLD_SECONDS = 600f   // 10:00 — same minute as the echo

    /**
     * Award the flown pilot's bandana if they survived past 10:00 and don't
     * already own it. Arms the return-to-bar ceremony via pending_bandana_pilot.
     * @return true iff a new bandana was awarded.
     */
    fun maybeAward(persistence: PersistenceManager, pilotId: String, survivalSeconds: Float): Boolean {
        if (survivalSeconds < THRESHOLD_SECONDS) return false
        if (persistence.hasBandana(pilotId)) return false
        persistence.addBandana(pilotId)
        persistence.setPendingBandanaPilot(pilotId)
        return true
    }
}
