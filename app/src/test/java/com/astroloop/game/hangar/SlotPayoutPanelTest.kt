package com.astroloop.game.hangar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astroloop.game.data.PersistenceManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The PAYOUTS panel is a sign hanging over the machine, and it must not lie about what the
 * machine pays.
 *
 * It did. The panel's numbers were written out by hand, and the casino rebalance (#23) changed
 * every cash payout underneath them: the sign advertised 2000/700/100/75/50 while the machine
 * actually paid 750/300/100/50/20. Nothing failed, because nothing ever compared the two.
 *
 * So this deliberately does not check the panel against a second copy of the same numbers — that
 * would only pin the derivation to itself and would have passed throughout the bug. It spins the
 * real machine across the whole roll space and asserts that every payout it hands out is the one
 * the panel advertises for the symbol that landed. The pairing between
 * [StorePageRenderer.PAYOUT_SYMBOLS] and `SlotOdds.CASH_TIERS` is hand-written and load-bearing;
 * this is what holds it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SlotPayoutPanelTest {

    /** Enough that no spin in the sweep can be refused for want of the 100 yen stake. */
    private val stakeFloat = 1_000_000

    private lateinit var persistence: PersistenceManager
    private lateinit var view: HangarSurfaceView

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        persistence = PersistenceManager(context)
        persistence.resetAllProgress()
        view = HangarSurfaceView(context) { _, _ -> }
    }

    /** What the panel claims each symbol is worth, read off the panel itself. */
    private fun advertised(): Map<Int, String> =
        StorePageRenderer.PAYOUT_SYMBOLS.zip(StorePageRenderer.PAYOUT_VALUES).toMap()

    @Test
    fun `every payout the machine hands out is the one the panel advertises`() {
        val panel = advertised()
        val seen = mutableSetOf<Int>()

        // 1000 evenly spaced rolls. The narrowest cash band is the diamond's 0.02, so every
        // symbol gets at least ~20 chances to turn up — see the coverage assertion below.
        for (i in 0 until 1000) {
            persistence.setYen(stakeFloat)
            view.state.actualYen = stakeFloat

            view.handleSlotSpin(roll = i / 1000f)
            val symbol = view.state.spinResultSymbol
            val yen = view.state.spinResultYen
            view.completeSpin(System.currentTimeMillis())

            // -1 is a loss and pays nothing; the jackpot's prize varies by save, which is exactly
            // why its row reads BONUS rather than a number.
            if (symbol < 0 || symbol == StorePageRenderer.SYM_ROCKET) continue

            seen.add(symbol)
            assertEquals(
                "the panel advertises ${panel[symbol]} for ${StorePageRenderer.getSymbolName(symbol)}" +
                    " but the machine paid $yen",
                yen.toString(),
                panel[symbol]
            )
        }

        // Without this the loop above could `continue` past every iteration and still pass.
        val cashSymbols = StorePageRenderer.PAYOUT_SYMBOLS
            .filter { it != StorePageRenderer.SYM_ROCKET }
            .toSet()
        assertEquals(
            "every cash symbol must have been rolled at least once, or this test measured nothing",
            cashSymbols,
            seen
        )
    }

    @Test
    fun `the panel has a row for every cash tier and one for the jackpot`() {
        assertEquals(
            "a symbol without a label (or the reverse) would draw a misaligned panel",
            StorePageRenderer.PAYOUT_SYMBOLS.size,
            StorePageRenderer.PAYOUT_VALUES.size
        )
        assertEquals(
            "the jackpot row plus one row per cash tier",
            com.astroloop.game.data.SlotOdds.CASH_TIERS.size + 1,
            StorePageRenderer.PAYOUT_SYMBOLS.size
        )
    }
}
