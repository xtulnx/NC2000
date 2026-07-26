package io.github.wangyu.nc2000.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlSceneJsonTest {
    @Test
    fun defaultGameSceneRoundTrips() {
        val original = ControlScene.defaultGameOverlay()
        val decoded = ControlSceneJson.decode(ControlSceneJson.encode(listOf(original))).single()

        assertEquals(original, decoded)
        assertEquals(LcdFrameStyle.CLASSIC_BEZEL, decoded.lcdFrameStyle)
        assertTrue(decoded.validationErrors().isEmpty())
        assertTrue(decoded.portrait.controls.any { it.shape == VirtualControlShape.CIRCLE })
        assertTrue(decoded.landscape.controls.any {
            it.action.kind == VirtualControlActionKind.OPEN_RUNTIME_MENU
        })
    }
}
