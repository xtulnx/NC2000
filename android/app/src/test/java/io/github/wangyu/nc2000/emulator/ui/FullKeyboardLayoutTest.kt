package io.github.wangyu.nc2000.emulator.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullKeyboardLayoutTest {
    @Test
    fun preservesExistingKeyIdsAndRowOrder() {
        val expectedRows = listOf(
            listOf(0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f),
            listOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15),
            listOf(0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x18, 0x1c),
            listOf(0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f, 0x19, 0x1d),
            listOf(0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x1a, 0x1e),
            listOf(0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x1b, 0x1f),
        )

        assertEquals(expectedRows, fullKeyboardRows.map { row -> row.map { it.keyId } })
        assertTrue(fullKeyboardRows.all { it.size <= 10 })
    }

    @Test
    fun rendersRequiredLowerRightMatrixWithoutChangingActions() {
        val actionIds = fullKeyboardRows.takeLast(2).map { row -> row.takeLast(4).map { it.keyId } }
        assertEquals(
            listOf(listOf(0x36, 0x37, 0x1a, 0x1e), listOf(0x3e, 0x3f, 0x1b, 0x1f)),
            actionIds,
        )

        val labels = actionIds.map { row ->
            row.map { id ->
                requireNotNull(fullKeyboardVisuals[id]).let { visual ->
                    visual.primaryLabel to visual.secondaryLabel
                }
            }
        }
        assertEquals(
            listOf(
                listOf("M" to "3", "⇞" to "税", "↑" to "−", "⇟" to "M−"),
                listOf("空格" to "=", "←" to null, "↓" to "+", "→" to "M+"),
            ),
            labels,
        )
        assertEquals("上翻页", fullKeyboardVisuals.getValue(0x37).contentDescription)
        assertEquals("下翻页", fullKeyboardVisuals.getValue(0x1e).contentDescription)
    }

    @Test
    fun everyActionKeyHasOneReadOnlyVisualSpec() {
        val actionIds = fullKeyboardRows.flatten().map { it.keyId }.toSet()
        assertEquals(actionIds, fullKeyboardVisuals.keys)
        assertEquals(0x39, fullKeyboardVisuals.getValue(0x39).keyId)
        assertEquals("SHIFT", fullKeyboardVisuals.getValue(0x39).legends.single().text)
    }

    @Test
    fun compactLayoutUsesOnlyCanonicalFullKeyboardKeys() {
        val directionIds = miniDirectionRows.flatten().filterNotNull()
        val actionIds = miniActionRows.flatten().filterNotNull()
        val compactIds = directionIds + actionIds

        assertEquals(
            listOf(0x1a, 0x3f, 0x1f, 0x1b),
            directionIds,
        )
        assertEquals(
            listOf(0x0f, 0x1d, 0x3b, 0x3e, 0x25, 0x35),
            actionIds,
        )
        assertEquals(
            listOf(0x1a, 0x3f, 0x1f, 0x1b, 0x0f, 0x1d, 0x3b, 0x3e, 0x25, 0x35),
            compactIds,
        )
        assertTrue(compactIds.all(fullKeyboardVisuals::containsKey))
    }

    @Test
    fun touchLayoutAddsHandZonesWithoutReorderingActions() {
        val touchRows = fullKeyboardTouchRows.map { row -> row.groups }

        assertEquals(fullKeyboardRows.drop(2), touchRows.map { groups -> groups.flatten() })
        assertEquals(
            listOf(listOf(5, 5), listOf(5, 5), listOf(5, 5), listOf(4, 3, 3)),
            touchRows.map { groups -> groups.map(List<*>::size) },
        )
        assertEquals(
            listOf(0x38, 0x39, 0x3a, 0x3b),
            touchRows.last().first().map(EmulatorKeySpec::keyId),
        )
        assertEquals(
            listOf(0x3f, 0x1b, 0x1f),
            touchRows.last().last().map(EmulatorKeySpec::keyId),
        )
    }

    @Test
    fun calculatorLegendsBelongToTheKeysPrintedBelowThem() {
        fun above(keyId: Int) = fullKeyboardVisuals.getValue(keyId).legends
            .single { it.position == LegendPosition.Above }.text

        assertEquals("10ˣ", above(0x28))
        assertEquals("eˣ", above(0x29))
        assertEquals("ʸ√x", above(0x2a))
        assertEquals("x²", above(0x2b))
        assertEquals(")", above(0x30))
        assertEquals("x!", above(0x31))
        assertEquals("0/±", above(0x32))
        assertTrue(fullKeyboardVisuals.getValue(0x20).legends.none {
            it.position == LegendPosition.Below
        })
    }
}
