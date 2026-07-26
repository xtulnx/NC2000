package io.github.wangyu.nc2000.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFileNameTest {
    @Test fun asciiSixteenBytesAreAccepted() {
        assertEquals(16, encodeDeviceFileName("1234567890abcdef").getOrThrow().size)
    }
    @Test fun asciiSeventeenBytesAreRejected() {
        assertTrue(encodeDeviceFileName("1234567890abcdefg").isFailure)
    }
    @Test fun gbkLengthAndUnmappableCharactersAreStrict() {
        assertEquals(4, encodeDeviceFileName("中文").getOrThrow().size)
        assertTrue(encodeDeviceFileName("emoji😀").isFailure)
    }
    @Test fun nulIsRejected() { assertTrue(encodeDeviceFileName("a\u0000b").isFailure) }
}
