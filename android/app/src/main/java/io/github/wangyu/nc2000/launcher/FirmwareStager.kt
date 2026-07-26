package io.github.wangyu.nc2000.launcher

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StagedFirmware(
    val romPath: String?,
    val norPath: String,
    val nandPath: String?,
    val nand0Path: String?,
    val statePath: String?,
)

/**
 * Copies SAF documents into private app storage before the native core opens
 * them. The core continues to work with ordinary file paths and never receives
 * a content URI. Re-selecting a different source replaces the staged copy;
 * otherwise writable flash/state data is preserved between launches.
 */
class FirmwareStager(private val context: Context) {
    suspend fun stage(profile: LaunchProfile): StagedFirmware = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "firmware/${profile.id}").apply { mkdirs() }
        StagedFirmware(
            romPath = stageOptional(profile.firmware.romUri, File(directory, "firmware.rom")),
            norPath = stageRequired(profile.firmware.norUri, File(directory, "firmware.nor")),
            nandPath = stageOptional(profile.firmware.nandUri, File(directory, "firmware.nand")),
            nand0Path = stageOptional(profile.firmware.nand0Uri, File(directory, "firmware.nand0")),
            statePath = stageOptional(profile.firmware.stateUri, File(directory, "firmware.state"))
                ?: File(directory, "firmware.state").absolutePath,
        )
    }

    private fun stageRequired(uri: String?, target: File): String {
        require(!uri.isNullOrBlank()) { "缺少固件文件：${target.extension.uppercase()}" }
        return stage(uri, target)
    }

    private fun stageOptional(uri: String?, target: File): String? =
        uri?.takeIf { it.isNotBlank() }?.let { stage(it, target) }

    private fun stage(uriValue: String, target: File): String {
        val sourceMarker = File(target.parentFile, "${target.name}.source")
        val sourceUnchanged = target.isFile &&
            sourceMarker.isFile &&
            sourceMarker.readText() == uriValue
        if (!sourceUnchanged) {
            val uri = Uri.parse(uriValue)
            val temporary = File(target.parentFile, "${target.name}.importing")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: throw FileNotFoundException("无法读取所选文件：$uri")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            sourceMarker.writeText(uriValue)
        }
        return target.absolutePath
    }
}
