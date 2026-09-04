package com.astroloop.game.data

/**
 * The Black Market slot machine's odds and payouts.
 *
 * Extracted out of `HangarSurfaceView` so the house edge is a number a test can pin rather than a
 * property of a `when` block nobody had added up. It had never been added up: the five cash tiers
 * alone returned 99 yen per 100 spent, and the jackpot pushed the machine 49% to 150% in the
 * player's favour depending on how expensive their next store upgrade happened to be. With
 * hold-to-spin, that is a printing press.
 *
 * The fix is not a flat rate. The jackpot's prize is a store upgrade whose cash value runs from
 * 1,000 to 50,000, so any fixed probability produces a wildly different edge at different points in
 * a save. Instead the jackpot slot is given a fixed **budget** — the yen it is allowed to return per
 * spin — and its probability is derived from whatever it is currently able to give away. A cheap
 * upgrade comes up often; the 50,000 one almost never. The edge does not move.
 */
object SlotOdds {

    const val SPIN_COST = 100

    /** What a jackpot pays once every store upgrade is maxed and there is no prize left to grant. */
    const val JACKPOT_CASH_PAYOUT = 10_000

    /** Yen the jackpot slot is budgeted to return per spin on an honest machine. */
    const val JACKPOT_BUDGET_BASE = 50f

    /**
     * The same budget, doubled, for the machine Rascal rigs after the desert.
     *
     * Doubling the budget rather than fixing a higher rate keeps the rigged machine honest about
     * *itself*: it pays twice as much, whatever it happens to be handing over, instead of becoming
     * a store-clearing engine the moment the player's next upgrade is an expensive one.
     */
    const val JACKPOT_BUDGET_RIGGED = 100f

    /** Probability band width and payout for each cash symbol, jackpot excluded. Diamond first. */
    val CASH_TIERS: List<Pair<Float, Int>> = listOf(
        0.02f to 750,   // diamond — honest machine; rigged widens this band, see diamondThreshold
        0.06f to 300,   // star
        0.06f to 100,   // yen
        0.08f to 50,    // bolt
        0.10f to 20,    // wrench
    )

    private const val DIAMOND_BAND_RIGGED = 0.04f

    fun diamondThreshold(rigged: Boolean): Float =
        if (rigged) DIAMOND_BAND_RIGGED else CASH_TIERS[0].first

    /**
     * The jackpot's probability, given what it would currently hand over.
     *
     * @param prizeValue the cash value of the prize — a store upgrade's next-level cost, or
     *   [JACKPOT_CASH_PAYOUT] once all eight are maxed.
     */
    fun jackpotThreshold(prizeValue: Int, rigged: Boolean): Float {
        val budget = if (rigged) JACKPOT_BUDGET_RIGGED else JACKPOT_BUDGET_BASE
        if (prizeValue <= 0) return 1f.coerceAtMost(budget / JACKPOT_CASH_PAYOUT)
        return (budget / prizeValue.toFloat()).coerceIn(0f, 1f)
    }

    /** Expected yen returned per [SPIN_COST] spin. 95 is a 5% house edge. */
    fun expectedReturn(prizeValue: Int, rigged: Boolean): Float {
        val jackpot = jackpotThreshold(prizeValue, rigged) * prizeValue
        val diamond = diamondThreshold(rigged) * CASH_TIERS[0].second
        val rest = CASH_TIERS.drop(1).fold(0f) { acc, (band, payout) -> acc + band * payout }
        return jackpot + diamond + rest
    }
}
