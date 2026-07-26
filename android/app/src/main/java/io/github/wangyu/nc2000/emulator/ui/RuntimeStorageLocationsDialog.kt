package io.github.wangyu.nc2000.emulator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wangyu.nc2000.launcher.FirmwareFiles
import java.io.File

private data class RuntimeStorageResource(
    val label: String,
    val description: String,
    val sourceUri: String?,
    val privatePath: String,
)

@Composable
internal fun RuntimeStorageLocationsDialog(
    profileId: String,
    firmware: FirmwareFiles,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resources = remember(profileId, firmware, context.filesDir) {
        runtimeStorageResources(context, profileId, firmware)
    }
    var status by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存储资源路径") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "模拟器实际读写的是应用私有副本；它受 Android 沙盒保护，系统文件管理器无法直接进入。来源 URI 对应你选择的原始文档，可复制或请求系统文件管理器定位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                resources.forEachIndexed { index, resource ->
                    if (index > 0) HorizontalDivider()
                    StorageResourceRow(
                        resource = resource,
                        onCopy = { label, value ->
                            copyToClipboard(context, label, value)
                            status = "已复制$label"
                        },
                        onLocate = { sourceUri ->
                            status = if (openInSystemFileManager(context, sourceUri)) {
                                "已请求系统文件管理器定位${resource.label}"
                            } else {
                                "系统文件管理器无法打开此文档"
                            }
                        },
                    )
                }
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun StorageResourceRow(
    resource: RuntimeStorageResource,
    onCopy: (String, String) -> Unit,
    onLocate: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(resource.label, fontWeight = FontWeight.SemiBold)
        Text(
            resource.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                "应用内路径\n${resource.privatePath}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { onCopy("${resource.label}应用内路径", resource.privatePath) }) {
            Text("复制应用内路径")
        }
        resource.sourceUri?.let { sourceUri ->
            SelectionContainer {
                Text(
                    "来源 URI\n$sourceUri",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onCopy("${resource.label}来源 URI", sourceUri) }) {
                    Text("复制来源 URI")
                }
                TextButton(onClick = { onLocate(sourceUri) }) {
                    Text("系统定位")
                }
            }
        } ?: Text(
            "未选择外部来源文档。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun runtimeStorageResources(
    context: Context,
    profileId: String,
    firmware: FirmwareFiles,
): List<RuntimeStorageResource> {
    val directory = File(context.filesDir, "firmware/$profileId")
    fun resource(
        label: String,
        description: String,
        sourceUri: String?,
        stagedFileName: String,
    ) = RuntimeStorageResource(
        label = label,
        description = description,
        sourceUri = sourceUri?.takeIf(String::isNotBlank),
        privatePath = File(directory, stagedFileName).absolutePath,
    )
    return listOf(
        resource("ROM", "只读固件", firmware.romUri, "firmware.rom"),
        resource("NOR", "可写闪存，保存文件和设置", firmware.norUri, "firmware.nor"),
        resource("NAND", "可写闪存", firmware.nandUri, "firmware.nand"),
        resource("NAND0", "可写闪存", firmware.nand0Uri, "firmware.nand0"),
        resource("STATE", "RAM、CPU 与外设运行现场", firmware.stateUri, "firmware.state"),
    )
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun openInSystemFileManager(context: Context, sourceUri: String): Boolean {
    val uri = Uri.parse(sourceUri)
    val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(openDocument)
    }.recoverCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.isSuccess
}
