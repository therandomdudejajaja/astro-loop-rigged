package com.astroloop.game.cabinet

import org.junit.Assert.*
import org.junit.Test

class CabinetFontTest {

    /** Every character the shell and HUD can render must exist. */
    private val required = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -.:?!¥"

    @Test fun everyRequiredGlyphIsDefined() {
        for (ch in required) {
            if (ch == ' ') continue
            assertNotNull("no glyph for '$ch'", CabinetFont.glyph(ch))
        }
    }

    @Test fun spaceIsDefinedButEmpty() {
        assertEquals(0, CabinetFont.glyph(' ')!!.size)
    }

    @Test fun glyphsStayInsideTheDesignGrid() {
        for (ch in required) {
            val g = CabinetFont.glyph(ch) ?: continue
            for (stroke in g) {
                assertEquals("'$ch' has an odd coordinate count", 0, stroke.size % 2)
                for (i in stroke.indices step 2) {
                    assertTrue("'$ch' x=${stroke[i]} out of grid", stroke[i] in 0f..6f)
                    assertTrue("'$ch' y=${stroke[i + 1]} out of grid", stroke[i + 1] in 0f..10f)
                }
            }
        }
    }

    @Test fun everyStrokeIsAtLeastALine() {
        for (ch in required) {
            val g = CabinetFont.glyph(ch) ?: continue
            for (stroke in g) {
                assertTrue("'$ch' has a stroke with fewer than two points", stroke.size >= 4)
            }
        }
    }

    @Test fun widthIsMonospacedAndScales() {
        assertEquals(3 * CabinetFont.ADVANCE * 2f, CabinetFont.width("DSH", 20f), 0.001f)
        assertEquals(
            CabinetFont.width("999", 20f) * 2f,
            CabinetFont.width("999", 40f),
            0.001f
        )
    }

    @Test fun lowercaseResolvesToUppercase() {
        assertNotNull(CabinetFont.glyph('a'))
        assertEquals(CabinetFont.glyph('A')!!.size, CabinetFont.glyph('a')!!.size)
    }

    @Test fun theBarsWidestRealisticLineFitsAPhone() {
        // The top bar carries initials, score and the longest power name. On a 1080px
        // playfield at the bar's size this must not exceed the width.
        val line = "WSK 999 FORTUNE"
        val size = 1080f / 26f
        assertTrue("bar line measures ${CabinetFont.width(line, size)}",
            CabinetFont.width(line, size) < 1080f)
    }
}
