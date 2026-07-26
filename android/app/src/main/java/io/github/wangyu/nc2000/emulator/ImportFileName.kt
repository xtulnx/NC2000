package io.github.wangyu.nc2000.emulator

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

/** Device filenames are raw GBK bytes, not UTF-16 Kotlin character counts. */
internal fun encodeDeviceFileName(name: String): Result<ByteArray> = runCatching {
    require(name.isNotEmpty()) { "设备文件名不能为空" }
    require('\u0000' !in name) { "设备文件名不能包含 NUL" }
    val encoder = Charset.forName("GBK").newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val encoded = encoder.encode(CharBuffer.wrap(name))
    val bytes = ByteArray(encoded.remaining()).also(encoded::get)
    require(bytes.size <= 16) { "设备文件名的 GBK 编码最多 16 字节（当前 ${bytes.size} 字节）" }
    bytes
}

internal data class ImportStatus(
    val state: String,
    val transferred: Long,
    val total: Long,
    val error: String,
) {
    val terminal get() = state == "succeeded" || state == "failed"
    val busy get() = state == "pending" || state == "running"

    companion object {
        fun parse(wire: String): ImportStatus {
            val parts = wire.split('|', limit = 4)
            return ImportStatus(
                parts.getOrElse(0) { "failed" },
                parts.getOrNull(1)?.toLongOrNull() ?: 0,
                parts.getOrNull(2)?.toLongOrNull() ?: 0,
                parts.getOrElse(3) { "导入状态不可用" },
            )
        }
    }
}
