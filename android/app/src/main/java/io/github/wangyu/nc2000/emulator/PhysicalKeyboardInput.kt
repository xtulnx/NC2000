package io.github.wangyu.nc2000.emulator

import android.view.KeyEvent

internal class PhysicalKeyboardInput(
    private val setKey: (keyId: Int, pressed: Boolean) -> Unit,
) {
    private val pressedKeyCodes = mutableSetOf<Int>()

    fun handle(keyCode: Int, pressed: Boolean): Boolean {
        val keyId = physicalKeyMap[keyCode] ?: return false
        if (pressed) {
            if (!pressedKeyCodes.add(keyCode)) return true
            val alreadyPressed = pressedKeyCodes.any {
                it != keyCode && physicalKeyMap[it] == keyId
            }
            if (!alreadyPressed) setKey(keyId, true)
        } else {
            if (!pressedKeyCodes.remove(keyCode)) return true
            val stillPressed = pressedKeyCodes.any { physicalKeyMap[it] == keyId }
            if (!stillPressed) setKey(keyId, false)
        }
        return true
    }

    fun releaseAll() {
        val keyIds = pressedKeyCodes.mapNotNull(physicalKeyMap::get).distinct()
        pressedKeyCodes.clear()
        keyIds.forEach { setKey(it, false) }
    }
}

internal val physicalKeyMap = mapOf(
    KeyEvent.KEYCODE_F5 to 0x0b,
    KeyEvent.KEYCODE_F6 to 0x0c,
    KeyEvent.KEYCODE_F7 to 0x0d,
    KeyEvent.KEYCODE_F8 to 0x0a,
    KeyEvent.KEYCODE_F9 to 0x09,
    KeyEvent.KEYCODE_F10 to 0x08,
    KeyEvent.KEYCODE_F11 to 0x0e,
    KeyEvent.KEYCODE_F12 to 0x0f,

    KeyEvent.KEYCODE_F1 to 0x10,
    KeyEvent.KEYCODE_INSERT to 0x10,
    KeyEvent.KEYCODE_F2 to 0x11,
    KeyEvent.KEYCODE_DEL to 0x11,
    KeyEvent.KEYCODE_FORWARD_DEL to 0x11,
    KeyEvent.KEYCODE_F3 to 0x12,
    KeyEvent.KEYCODE_F4 to 0x13,
    KeyEvent.KEYCODE_APOSTROPHE to 0x14,
    KeyEvent.KEYCODE_SEMICOLON to 0x15,

    KeyEvent.KEYCODE_O to 0x18,
    KeyEvent.KEYCODE_L to 0x19,
    KeyEvent.KEYCODE_DPAD_UP to 0x1a,
    KeyEvent.KEYCODE_DPAD_DOWN to 0x1b,
    KeyEvent.KEYCODE_P to 0x1c,
    KeyEvent.KEYCODE_ENTER to 0x1d,
    KeyEvent.KEYCODE_NUMPAD_ENTER to 0x1d,
    KeyEvent.KEYCODE_PAGE_DOWN to 0x1e,
    KeyEvent.KEYCODE_SLASH to 0x1e,
    KeyEvent.KEYCODE_NUMPAD_DIVIDE to 0x1e,
    KeyEvent.KEYCODE_DPAD_RIGHT to 0x1f,

    KeyEvent.KEYCODE_Q to 0x20,
    KeyEvent.KEYCODE_W to 0x21,
    KeyEvent.KEYCODE_E to 0x22,
    KeyEvent.KEYCODE_R to 0x23,
    KeyEvent.KEYCODE_T to 0x24,
    KeyEvent.KEYCODE_7 to 0x24,
    KeyEvent.KEYCODE_NUMPAD_7 to 0x24,
    KeyEvent.KEYCODE_Y to 0x25,
    KeyEvent.KEYCODE_8 to 0x25,
    KeyEvent.KEYCODE_NUMPAD_8 to 0x25,
    KeyEvent.KEYCODE_U to 0x26,
    KeyEvent.KEYCODE_9 to 0x26,
    KeyEvent.KEYCODE_NUMPAD_9 to 0x26,
    KeyEvent.KEYCODE_I to 0x27,

    KeyEvent.KEYCODE_A to 0x28,
    KeyEvent.KEYCODE_S to 0x29,
    KeyEvent.KEYCODE_D to 0x2a,
    KeyEvent.KEYCODE_F to 0x2b,
    KeyEvent.KEYCODE_G to 0x2c,
    KeyEvent.KEYCODE_4 to 0x2c,
    KeyEvent.KEYCODE_NUMPAD_4 to 0x2c,
    KeyEvent.KEYCODE_H to 0x2d,
    KeyEvent.KEYCODE_5 to 0x2d,
    KeyEvent.KEYCODE_NUMPAD_5 to 0x2d,
    KeyEvent.KEYCODE_J to 0x2e,
    KeyEvent.KEYCODE_6 to 0x2e,
    KeyEvent.KEYCODE_NUMPAD_6 to 0x2e,
    KeyEvent.KEYCODE_K to 0x2f,

    KeyEvent.KEYCODE_Z to 0x30,
    KeyEvent.KEYCODE_X to 0x31,
    KeyEvent.KEYCODE_C to 0x32,
    KeyEvent.KEYCODE_V to 0x33,
    KeyEvent.KEYCODE_B to 0x34,
    KeyEvent.KEYCODE_1 to 0x34,
    KeyEvent.KEYCODE_NUMPAD_1 to 0x34,
    KeyEvent.KEYCODE_N to 0x35,
    KeyEvent.KEYCODE_2 to 0x35,
    KeyEvent.KEYCODE_NUMPAD_2 to 0x35,
    KeyEvent.KEYCODE_M to 0x36,
    KeyEvent.KEYCODE_3 to 0x36,
    KeyEvent.KEYCODE_NUMPAD_3 to 0x36,
    KeyEvent.KEYCODE_PAGE_UP to 0x37,
    KeyEvent.KEYCODE_COMMA to 0x37,

    KeyEvent.KEYCODE_HELP to 0x38,
    KeyEvent.KEYCODE_LEFT_BRACKET to 0x38,
    KeyEvent.KEYCODE_SHIFT_LEFT to 0x39,
    KeyEvent.KEYCODE_SHIFT_RIGHT to 0x39,
    KeyEvent.KEYCODE_RIGHT_BRACKET to 0x39,
    KeyEvent.KEYCODE_CAPS_LOCK to 0x3a,
    KeyEvent.KEYCODE_BACKSLASH to 0x3a,
    KeyEvent.KEYCODE_ESCAPE to 0x3b,
    KeyEvent.KEYCODE_0 to 0x3c,
    KeyEvent.KEYCODE_NUMPAD_0 to 0x3c,
    KeyEvent.KEYCODE_PERIOD to 0x3d,
    KeyEvent.KEYCODE_NUMPAD_DOT to 0x3d,
    KeyEvent.KEYCODE_SPACE to 0x3e,
    KeyEvent.KEYCODE_EQUALS to 0x3e,
    KeyEvent.KEYCODE_NUMPAD_EQUALS to 0x3e,
    KeyEvent.KEYCODE_DPAD_LEFT to 0x3f,
)
