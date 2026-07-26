package io.github.wangyu.nc2000.emulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wangyu.nc2000.controls.LcdFrameStyle
import io.github.wangyu.nc2000.launcher.FirmwareFiles
import io.github.wangyu.nc2000.ui.theme.NcColorTokens

enum class RuntimeLcdPalette {
    CLASSIC,
    CLEAR,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeControlSheet(
    profileId: String,
    firmware: FirmwareFiles,
    palette: RuntimeLcdPalette,
    frameStyle: LcdFrameStyle,
    fastForward: Boolean,
    fastForwardMultiplier: Double?,
    message: String?,
    backgroundContinues: Boolean,
    autoSaveFlash: Boolean,
    autoSaveState: Boolean,
    onPaletteChange: (RuntimeLcdPalette) -> Unit,
    onFrameStyleChange: (LcdFrameStyle) -> Unit,
    onFastForwardChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSaveNor: () -> Unit,
    onLoadNor: () -> Unit,
    onSaveState: () -> Unit,
    onLoadState: () -> Unit,
    onImport: () -> Unit,
    importBusy: Boolean,
    onBackgroundContinuesChange: (Boolean) -> Unit,
    onBackground: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    var showStorageLocations by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NcColorTokens.SurfaceWarm,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "运行控制",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) { Text("完成") }
            }

            Text("手动存档", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveNor, modifier = Modifier.weight(1f)) { Text("保存 NOR") }
                OutlinedButton(onClick = onLoadNor, modifier = Modifier.weight(1f)) {
                    Text("读取 NOR")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveState, modifier = Modifier.weight(1f)) { Text("保存 STATE") }
                OutlinedButton(onClick = onLoadState, modifier = Modifier.weight(1f)) {
                    Text("读取 STATE")
                }
            }
            Text(
                "NOR 保存设备文件和设置（需要 NAND 的机型会同时处理 NAND/NAND0）；STATE 只保存 RAM、CPU 和外设现场，文件较小，相当于保持设备不断电。两者可独立保存和读取。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { showStorageLocations = true }, modifier = Modifier.fillMaxWidth()) {
                Text("查看存储资源路径")
            }
            OutlinedButton(
                onClick = onImport,
                enabled = !importBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (importBusy) "正在导入文件…" else "导入文件到当前目录")
            }

            HorizontalDivider()
            Text("应用切到后台时（仅当前会话）", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !backgroundContinues,
                    onClick = { onBackgroundContinuesChange(false) },
                    label = { Text("自动暂停（省电）") },
                )
                FilterChip(
                    selected = backgroundContinues,
                    onClick = { onBackgroundContinuesChange(true) },
                    label = { Text("持续运行（挂机）") },
                )
            }
            Text(
                if (backgroundContinues) {
                    "应用切到后台或息屏后游戏仍会推进；需要保持 CPU 唤醒，耗电明显增加。"
                } else {
                    "应用切到后台时冻结当前现场；返回应用后立即继续，不需要读取存档。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text("LCD 显示效果", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = palette == RuntimeLcdPalette.CLASSIC,
                    onClick = { onPaletteChange(RuntimeLcdPalette.CLASSIC) },
                    label = { Text("经典") },
                )
                FilterChip(
                    selected = palette == RuntimeLcdPalette.CLEAR,
                    onClick = { onPaletteChange(RuntimeLcdPalette.CLEAR) },
                    label = { Text("清晰") },
                )
            }

            Text("LCD 边框", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = frameStyle == LcdFrameStyle.SIMPLE,
                    onClick = { onFrameStyleChange(LcdFrameStyle.SIMPLE) },
                    label = { Text("简洁") },
                )
                FilterChip(
                    selected = frameStyle == LcdFrameStyle.CLASSIC_BEZEL,
                    onClick = { onFrameStyleChange(LcdFrameStyle.CLASSIC_BEZEL) },
                    label = { Text("经典机身") },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("快速播放", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (fastForward) {
                            fastForwardMultiplier?.let {
                                "当前实际倍率：${formatMultiplier(it)}；长按加速键可临时恢复原速"
                            } ?: "正在测量实际倍率…；长按加速键可临时恢复原速"
                        } else {
                            "单击加速键锁定加速；长按临时加速"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = fastForward, onCheckedChange = onFastForwardChange)
            }

            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("复位模拟器")
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()
            OutlinedButton(onClick = onBackground, modifier = Modifier.fillMaxWidth()) {
                Text("返回启动器 · 持续运行")
            }
            OutlinedButton(
                onClick = { confirmStop = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("结束模拟器")
            }
            Spacer(Modifier.padding(bottom = 2.dp))
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("结束模拟器？") },
            text = {
                Text(
                    when {
                        autoSaveFlash && autoSaveState ->
                            "将分别自动保存 NOR 持久存储和 STATE 运行现场，然后返回启动器。ROM 不会改动。"
                        autoSaveFlash ->
                            "将只自动保存 NOR 持久存储，不保存 STATE 运行现场，然后返回启动器。"
                        autoSaveState ->
                            "将只自动保存 STATE 运行现场，不保存 NOR 持久存储，然后返回启动器。"
                        else ->
                            "当前未启用退出自动保存。结束后未手动保存的 NOR 和 STATE 变化会丢失。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStop = false
                        onStop()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("取消") }
            },
        )
    }

    if (showStorageLocations) {
        RuntimeStorageLocationsDialog(
            profileId = profileId,
            firmware = firmware,
            onDismiss = { showStorageLocations = false },
        )
    }
}

private fun formatMultiplier(multiplier: Double): String {
    val rounded = (multiplier * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) "×${rounded.toInt()}" else "×$rounded"
}
