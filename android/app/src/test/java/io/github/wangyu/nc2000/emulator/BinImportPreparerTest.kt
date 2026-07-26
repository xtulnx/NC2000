package io.github.wangyu.nc2000.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinImportPreparerTest {
    @Test fun automaticModeKeepsRawBinUnchanged() {
        val source = byteArrayOf(0xAE.toByte(), 0xEE.toByte(), 0xEA.toByte(), 1, 2, 3)
        val (transform, result) = process(source, BinImportMode.AUTO)

        assertEquals(BinImportTransform.DIRECT, transform)
        assertArrayEquals(source, result)
    }

    @Test fun automaticModeRemovesApplicationAttributeHeader() {
        val body = byteArrayOf(0xAE.toByte(), 0xEE.toByte(), 0xEA.toByte(), 7, 8)
        val source = ByteArray(48) { 0x20 }.also {
            "Application".encodeToByteArray().copyInto(it)
        } + body
        val (transform, result) = process(source, BinImportMode.AUTO)

        assertEquals(BinImportTransform.TRIMMED_ATTRIBUTE_HEADER, transform)
        assertArrayEquals(body, result)
    }

    @Test fun directModeNeverInterpretsTheFile() {
        val source = "not an NC binary".encodeToByteArray()
        val (transform, result) = process(source, BinImportMode.DIRECT)

        assertEquals(BinImportTransform.DIRECT, transform)
        assertArrayEquals(source, result)
    }

    @Test fun automaticModeDecryptsValidatedEncryptedContainer() {
        val keyTable = identityKeyTable()
        val body = ByteArray(40) { (it * 7).toByte() }.also {
            it[0] = 0xAE.toByte()
            it[1] = 0xEE.toByte()
            it[2] = 0xEA.toByte()
        }
        val encrypted = identityEncryptedContainer(body)
        val output = ByteArrayOutputStream()

        val transform = NcBinImportProcessor.process(
            ByteArrayInputStream(encrypted),
            output,
            BinImportMode.AUTO,
            keyTable,
        )

        assertEquals(BinImportTransform.DECRYPTED, transform)
        assertArrayEquals(body, output.toByteArray())
    }

    @Test fun unrecognizedAutomaticInputSuggestsDirectImport() {
        val error = runCatching {
            process("ordinary file".encodeToByteArray(), BinImportMode.AUTO)
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("原样导入"))
    }

    private fun process(
        source: ByteArray,
        mode: BinImportMode,
    ): Pair<BinImportTransform, ByteArray> {
        val output = ByteArrayOutputStream()
        val transform = NcBinImportProcessor.process(
            ByteArrayInputStream(source),
            output,
            mode,
            if (mode == BinImportMode.AUTO) identityKeyTable() else null,
        )
        return transform to output.toByteArray()
    }

    private fun identityKeyTable(): ByteArray = ByteArray(256 * 256) { it.toByte() }

    /** Identity substitution keeps ciphertext readable while exercising container/key handling. */
    private fun identityEncryptedContainer(body: ByteArray): ByteArray {
        val key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val keyDelta = 12
        val obscuredKey = ByteArray(key.size) {
            (key[it].toInt() xor body[keyDelta + it].toInt()).toByte()
        }
        val header = ByteArray(80).also {
            it[0] = keyDelta.toByte()
            it[1] = (keyDelta ushr 8).toByte()
            writeVdir(it, 2, "ggvroot/")
            writeVdir(it, 28, "ggvfile/")
            writeVdir(it, 54, "ggvattr/")
        }
        return byteArrayOf(key.size.toByte()) + obscuredKey + header + body
    }

    private fun writeVdir(target: ByteArray, offset: Int, prefix: String) {
        prefix.encodeToByteArray().copyInto(target, offset)
        val content = "fixture".padEnd(16).encodeToByteArray()
        content.copyInto(target, offset + 8)
        var xor = 0
        var sum = 0
        content.forEach {
            xor = xor xor (it.toInt() and 0xFF)
            sum += it.toInt() and 0xFF
        }
        target[offset + 24] = xor.toByte()
        target[offset + 25] = sum.toByte()
    }
}
