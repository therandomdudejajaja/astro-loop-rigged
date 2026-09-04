package com.astroloop.game.system

/**
 * The crystal's five escalation phases. Originally declared alongside the (now deleted)
 * CrystalFightSystem; kept standalone because CrystalFightLines.taunt(p: CrystalPhase) still
 * needs it — taunt/opening/barChatter survive the old reckoning's excision.
 */
enum class CrystalPhase { P1, P2, P3, P4, P5 }
