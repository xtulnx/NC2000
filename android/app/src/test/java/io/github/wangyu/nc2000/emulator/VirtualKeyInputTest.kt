package io.github.wangyu.nc2000.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualKeyInputTest {
    @Test
    fun releasesEveryPressedKeyWhenRuntimeControlsOpen() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val input = VirtualKeyInput { keyId, pressed -> events += keyId to pressed }

        input.setKey(0x28, true)
        input.setKey(0x29, true)
        input.releaseAll()
        input.setKey(0x28, false)

        assertEquals(
            listOf(0x28 to true, 0x29 to true, 0x28 to false, 0x29 to false),
            events,
        )
    }

    @Test
    fun ordinaryPressAndReleaseKeepsExistingEventOrder() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val input = VirtualKeyInput { keyId, pressed -> events += keyId to pressed }

        input.setKey(0x1d, true)
        input.setKey(0x1d, false)
        input.releaseAll()

        assertEquals(listOf(0x1d to true, 0x1d to false), events)
    }
}
