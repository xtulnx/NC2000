package io.github.wangyu.nc2000.emulator

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

internal enum class BinImportMode {
    AUTO,
    DIRECT,
}

internal enum class BinImportTransform {
    DIRECT,
    TRIMMED_ATTRIBUTE_HEADER,
    DECRYPTED,
}

internal data class PreparedImport(
    val file: File,
    val transform: BinImportTransform,
)

/**
 * Copies the selected SAF document to an app-private temporary file and, in
 * automatic mode, converts an NC encrypted/attribute-header BIN to the raw
 * bytes expected by the emulator's put command. Native code only sees [File].
 */
internal fun prepareImportToCache(
    context: Context,
    uri: Uri,
    mode: BinImportMode,
): PreparedImport {
    val directory = File(context.cacheDir, "imports").apply {
        check(isDirectory || mkdirs()) { "无法创建导入临时目录" }
    }
    val id = UUID.randomUUID().toString()
    val partial = File(directory, "$id.part")
    val target = File(directory, "$id.bin")
    try {
        val transform = context.contentResolver.openInputStream(uri)?.use { input ->
            partial.outputStream().use { output ->
                NcBinImportProcessor.process(
                    input = input,
                    output = output,
                    mode = mode,
                    keyTable = if (mode == BinImportMode.AUTO) {
                        context.assets.open(NC_KEY_TABLE_ASSET).use(InputStream::readBytes)
                    } else {
                        null
                    },
                )
            }
        } ?: error("无法读取所选文件")
        check(partial.renameTo(target)) { "无法保存导入临时文件" }
        return PreparedImport(target, transform)
    } catch (error: Exception) {
        partial.delete()
        target.delete()
        throw error
    }
}

internal object NcBinImportProcessor {
    private const val ATTRIBUTE_HEADER_SIZE = 48
    private const val ENCRYPTED_HEADER_SIZE = 80
    private const val VDIR_SIZE = 26
    private val currentRawMagic = byteArrayOf(0xAE.toByte(), 0xEE.toByte(), 0xEA.toByte())
    private val legacyRawMagic = byteArrayOf(0xAA.toByte(), 0xA5.toByte(), 0x5A.toByte())
    private val attributePrefix = "Application     ".encodeToByteArray()
    private val directoryPrefix = "ggvroot/".encodeToByteArray()
    private val filePrefix = "ggvfile/".encodeToByteArray()
    private val attributesPrefix = "ggvattr/".encodeToByteArray()

    fun process(
        input: InputStream,
        output: OutputStream,
        mode: BinImportMode,
        keyTable: ByteArray?,
    ): BinImportTransform {
        if (mode == BinImportMode.DIRECT) {
            input.copyTo(output)
            return BinImportTransform.DIRECT
        }

        val source = input.readBytes()
        require(source.isNotEmpty()) { "所选文件为空" }
        return when {
            source.startsWith(currentRawMagic) || source.startsWith(legacyRawMagic) -> {
                output.write(source)
                BinImportTransform.DIRECT
            }
            source.startsWith(attributePrefix) -> {
                require(source.size > ATTRIBUTE_HEADER_SIZE) { "Application 属性头后没有可导入的数据" }
                output.write(source, ATTRIBUTE_HEADER_SIZE, source.size - ATTRIBUTE_HEADER_SIZE)
                BinImportTransform.TRIMMED_ATTRIBUTE_HEADER
            }
            else -> {
                val table = requireNotNull(keyTable) { "缺少 BIN 解密查找表" }
                output.write(decrypt(source, table))
                BinImportTransform.DECRYPTED
            }
        }
    }

    private fun decrypt(source: ByteArray, keyTable: ByteArray): ByteArray {
        require(keyTable.size == 256 * 256) { "BIN 解密查找表大小不正确" }
        require(source.size >= 1 + 8 + ENCRYPTED_HEADER_SIZE + 3) { encryptedFormatError() }

        val keyLength = source[0].toInt() and 0xFF
        require(keyLength in 8..24) { encryptedFormatError("密钥长度异常：$keyLength") }
        val headerStart = 1 + keyLength
        val bodyStart = headerStart + ENCRYPTED_HEADER_SIZE
        require(source.size >= bodyStart + 3) { encryptedFormatError() }

        val key = source.copyOfRange(1, headerStart)
        val header = source.copyOfRange(headerStart, bodyStart)
        val body = source.copyOfRange(bodyStart, source.size)
        val keyDelta = (header[0].toInt() and 0xFF) or ((header[1].toInt() and 0xFF) shl 8)
        require(keyDelta <= body.size - keyLength) {
            encryptedFormatError("密钥位置越界：$keyDelta+$keyLength > ${body.size}")
        }
        for (index in key.indices) {
            key[index] = (key[index].toInt() xor body[keyDelta + index].toInt()).toByte()
        }

        val decodeTable = inverseTable(keyTable)
        var currentKey = 0
        for (index in 2 until ENCRYPTED_HEADER_SIZE) {
            header[index] = decode(
                decodeTable,
                key[currentKey % keyLength],
                header[index],
            )
            currentKey++
        }

        require(checkVdir(header, 2, directoryPrefix)) { encryptedFormatError("目录头校验失败") }
        require(checkVdir(header, 2 + VDIR_SIZE, filePrefix)) { encryptedFormatError("文件头校验失败") }
        require(checkVdir(header, 2 + 2 * VDIR_SIZE, attributesPrefix)) {
            encryptedFormatError("属性头校验失败")
        }

        for (index in 0 until keyDelta) {
            body[index] = decode(decodeTable, key[currentKey % keyLength], body[index])
            currentKey++
        }
        for (index in keyDelta + keyLength until body.size) {
            body[index] = decode(decodeTable, key[currentKey % keyLength], body[index])
            currentKey++
        }
        return body
    }

    private fun inverseTable(keyTable: ByteArray): ByteArray {
        val inverse = ByteArray(keyTable.size)
        for (key in 0 until 256) {
            val row = key * 256
            for (plain in 0 until 256) {
                val encrypted = keyTable[row + plain].toInt() and 0xFF
                inverse[row + encrypted] = plain.toByte()
            }
        }
        return inverse
    }

    private fun decode(table: ByteArray, key: Byte, encrypted: Byte): Byte =
        table[(key.toInt() and 0xFF) * 256 + (encrypted.toInt() and 0xFF)]

    private fun checkVdir(header: ByteArray, offset: Int, prefix: ByteArray): Boolean {
        if (!header.regionMatches(offset, prefix)) return false
        var xor = header[offset + 24].toInt() and 0xFF
        var sum = header[offset + 25].toInt() and 0xFF
        for (index in offset + 8 until offset + 24) {
            val value = header[index].toInt() and 0xFF
            xor = xor xor value
            sum = (sum - value) and 0xFF
        }
        return xor == 0 && sum == 0
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = regionMatches(0, prefix)

    private fun ByteArray.regionMatches(offset: Int, expected: ByteArray): Boolean =
        offset >= 0 && size - offset >= expected.size && expected.indices.all {
            this[offset + it] == expected[it]
        }

    private fun encryptedFormatError(detail: String? = null): String = buildString {
        append("无法识别或解密 BIN 文件")
        if (detail != null) append("（$detail）")
        append("；如需保持原文件，请选择“原样导入”")
    }
}

private const val NC_KEY_TABLE_ASSET = "nc_keytab.bin"
