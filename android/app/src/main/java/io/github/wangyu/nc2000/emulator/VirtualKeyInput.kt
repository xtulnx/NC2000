package io.github.wangyu.nc2000.emulator

internal class VirtualKeyInput(
    private val sendKey: (keyId: Int, pressed: Boolean) -> Unit,
) {
    private val pressedKeyCounts = mutableMapOf<Int, Int>()

    fun setKey(keyId: Int, pressed: Boolean) {
        if (pressed) {
            pressedKeyCounts[keyId] = pressedKeyCounts.getOrDefault(keyId, 0) + 1
            sendKey(keyId, true)
            return
        }

        val count = pressedKeyCounts[keyId] ?: return
        if (count == 1) pressedKeyCounts.remove(keyId) else pressedKeyCounts[keyId] = count - 1
        sendKey(keyId, false)
    }

    fun releaseAll() {
        val pressedKeyIds = pressedKeyCounts.keys.toList()
        pressedKeyCounts.clear()
        pressedKeyIds.forEach { sendKey(it, false) }
    }
}
