package io.github.wangyu.nc2000.emulator

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardInputTest {
    @Test
    fun mapsTypingAndNavigationKeys() {
        assertEquals(0x28, physicalKeyMap[KeyEvent.KEYCODE_A])
        assertEquals(0x34, physicalKeyMap[KeyEvent.KEYCODE_1])
        assertEquals(0x1d, physicalKeyMap[KeyEvent.KEYCODE_ENTER])
        assertEquals(0x1a, physicalKeyMap[KeyEvent.KEYCODE_DPAD_UP])
        assertEquals(0x0f, physicalKeyMap[KeyEvent.KEYCODE_F12])
    }

    @Test
    fun sendsOnePressForAliasedPhysicalKeys() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val input = PhysicalKeyboardInput { keyId, pressed -> events += keyId to pressed }

        assertTrue(input.handle(KeyEvent.KEYCODE_N, true))
        assertTrue(input.handle(KeyEvent.KEYCODE_2, true))
        assertTrue(input.handle(KeyEvent.KEYCODE_N, false))
        assertTrue(input.handle(KeyEvent.KEYCODE_2, false))

        assertEquals(listOf(0x35 to true, 0x35 to false), events)
    }

    @Test
    fun ignoresRepeatsAndReleasesPressedKeysOnFocusLoss() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val input = PhysicalKeyboardInput { keyId, pressed -> events += keyId to pressed }

        assertTrue(input.handle(KeyEvent.KEYCODE_A, true))
        assertTrue(input.handle(KeyEvent.KEYCODE_A, true))
        assertFalse(input.handle(KeyEvent.KEYCODE_VOLUME_UP, true))
        input.releaseAll()

        assertEquals(listOf(0x28 to true, 0x28 to false), events)
    }
}
