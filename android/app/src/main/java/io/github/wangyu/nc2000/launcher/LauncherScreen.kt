package io.github.wangyu.nc2000.launcher

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.wangyu.nc2000.controls.ControlScene
import io.github.wangyu.nc2000.controls.ControlSceneEditorDialog
import io.github.wangyu.nc2000.emulator.RunningEmulatorSession
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    profiles: List<LaunchProfile>,
    controlScenes: List<ControlScene>,
    nativeBuildInfo: String,
    message: String?,
    runningSessions: List<RunningEmulatorSession>,
    onMessageShown: () -> Unit,
    onSaveProfile: (LaunchProfile) -> Unit,
    onDuplicateProfile: (LaunchProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onMoveProfile: (String, Int) -> Unit,
    onPickFirmware: (String) -> Unit,
    onPickIcon: (String) -> Unit,
    onSaveControlScene: (ControlScene) -> Unit,
    onDeleteControlScene: (String) -> Unit,
    onLaunch: (LaunchProfile) -> Unit,
) {
    var editingProfile by remember { mutableStateOf<LaunchProfile?>(null) }
    var editingControlScenes by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NC2000 Emulator")
                        Text(
                            text = if (runningSessions.isEmpty()) {
                                "选择一个配置启动"
                            } else {
                                "后台运行：${runningSessions.size} 台模拟器"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = nativeBuildInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { editingControlScenes = true }) {
                        Text("控制场景")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProfile = LaunchProfile(
                        id = UUID.randomUUID().toString(),
                        name = "新启动入口",
                        model = MachineModel.NC1020,
                    )
                },
            ) { Text("＋", style = MaterialTheme.typography.headlineMedium) }
        },
    ) { contentPadding ->
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有启动入口，点击右下角添加。")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                    ProfileCard(
                        profile = profile,
                        controlSceneName = controlScenes.firstOrNull {
                            it.id == profile.controlSceneId
                        }?.name,
                        runningSession = runningSessions.firstOrNull { it.profileId == profile.id },
                        canMoveUp = index > 0,
                        canMoveDown = index < profiles.lastIndex,
                        onEdit = { editingProfile = profile },
                        onDuplicate = { onDuplicateProfile(profile) },
                        onDelete = { onDeleteProfile(profile.id) },
                        onMoveUp = { onMoveProfile(profile.id, -1) },
                        onMoveDown = { onMoveProfile(profile.id, 1) },
                        onPickFirmware = { onPickFirmware(profile.id) },
                        onPickIcon = { onPickIcon(profile.id) },
                        onLaunch = { onLaunch(profile) },
                    )
                }
            }
        }
    }

    editingProfile?.let { profile ->
        ProfileEditorDialog(
            profile = profile,
            controlScenes = controlScenes,
            onDismiss = { editingProfile = null },
            onSave = {
                onSaveProfile(it)
                editingProfile = null
            },
        )
    }

    if (editingControlScenes) {
        ControlSceneEditorDialog(
            scenes = controlScenes,
            onSave = onSaveControlScene,
            onDelete = onDeleteControlScene,
            onDismiss = { editingControlScenes = false },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: LaunchProfile,
    controlSceneName: String?,
    runningSession: RunningEmulatorSession?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPickFirmware: () -> Unit,
    onPickIcon: () -> Unit,
    onLaunch: () -> Unit,
) {
    val errors = profile.validationErrors()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileIconView(profile.icon)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = profile.model.displayName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "控制：${controlSceneName ?: "默认键盘"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onLaunch, enabled = runningSession != null || errors.isEmpty()) {
                    Text(
                        runningSession?.let { "返回 #${it.slot}" } ?: "启动",
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = firmwareSummary(profile),
                style = MaterialTheme.typography.bodySmall,
                color = if (errors.isEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPickFirmware) { Text("选择固件") }
                OutlinedButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onPickIcon) { Text("图标") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDuplicate) { Text("复制") }
                TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("上移") }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("下移") }
                TextButton(onClick = onDelete, enabled = runningSession == null) { Text("删除") }
            }
        }
    }
}

@Composable
private fun ProfileIconView(icon: ProfileIcon) {
    val context = LocalContext.current
    var bitmap by remember(icon) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(icon) {
        bitmap = if (icon.kind == ProfileIconKind.CUSTOM_URI) {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(icon.value))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        } else {
            null
        }
    }

    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = builtInIconGlyph(icon.value),
                style = MaterialTheme.typography.headlineLarge,
            )
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    profile: LaunchProfile,
    controlScenes: List<ControlScene>,
    onDismiss: () -> Unit,
    onSave: (LaunchProfile) -> Unit,
) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    var model by remember(profile) { mutableStateOf(profile.model) }
    var features by remember(profile) { mutableStateOf(profile.features) }
    var icon by remember(profile) { mutableStateOf(profile.icon) }
    var controlSceneId by remember(profile) { mutableStateOf(profile.controlSceneId) }
    var overclockText by remember(profile) { mutableStateOf(profile.features.overclockFactor.toString()) }
    var fastForwardText by remember(profile) { mutableStateOf(profile.features.fastForwardLimit.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("启动配置") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("入口名称") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("机型", fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MachineModel.entries.forEach { option ->
                            FilterChip(
                                selected = model == option,
                                onClick = { model = option },
                                label = { Text(option.displayName) },
                            )
                        }
                    }
                }
                item {
                    Text("内置图标", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("calculator", "book", "game", "chip").forEach { value ->
                            FilterChip(
                                selected = icon.kind == ProfileIconKind.BUILT_IN && icon.value == value,
                                onClick = { icon = ProfileIcon(ProfileIconKind.BUILT_IN, value) },
                                label = { Text(builtInIconGlyph(value)) },
                            )
                        }
                    }
                }
                item {
                    Text("控制布局", fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = controlSceneId == null,
                            onClick = { controlSceneId = null },
                            label = { Text("默认迷你/全键盘") },
                        )
                        controlScenes.forEach { scene ->
                            FilterChip(
                                selected = controlSceneId == scene.id,
                                onClick = { controlSceneId = scene.id },
                                label = { Text(scene.name) },
                            )
                        }
                    }
                }
                item {
                    Text(
                        "ROM 是只读固件。可写存储指 NOR/NAND/NAND0；运行现场指 RAM、CPU 和外设状态（STATE）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FeatureSwitch(
                        "启动时恢复运行现场",
                        features.loadState,
                        "读取上次保存或导入的 STATE（RAM/CPU），并与当前可写存储搭配恢复。",
                    ) {
                        features = features.copy(loadState = it)
                    }
                    FeatureSwitch(
                        "退出时自动保存 NOR",
                        features.autoSaveFlash,
                        "保存持久存储；需要 NAND 的机型也会一并保存 NAND/NAND0。",
                    ) {
                        features = features.copy(autoSaveFlash = it)
                    }
                    FeatureSwitch(
                        "退出时自动保存 STATE",
                        features.autoSaveState,
                        "只保存 RAM、CPU 和外设运行现场；与 NOR 自动保存相互独立。",
                    ) {
                        features = features.copy(autoSaveState = it)
                    }
                    Text("快捷存档/读档内容", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !features.quickSaveFlash && features.quickSaveState,
                            onClick = {
                                features = features.copy(
                                    quickSaveFlash = false,
                                    quickSaveState = true,
                                )
                            },
                            label = { Text("仅 STATE") },
                        )
                        FilterChip(
                            selected = features.quickSaveFlash && !features.quickSaveState,
                            onClick = {
                                features = features.copy(
                                    quickSaveFlash = true,
                                    quickSaveState = false,
                                )
                            },
                            label = { Text("仅 NOR") },
                        )
                        FilterChip(
                            selected = features.quickSaveFlash && features.quickSaveState,
                            onClick = {
                                features = features.copy(
                                    quickSaveFlash = true,
                                    quickSaveState = true,
                                )
                            },
                            label = { Text("NOR + STATE") },
                        )
                    }
                    Text(
                        "STATE 文件较小，适合频繁快存/快读，相当于保持设备不断电；NOR 用于保留设备文件与设置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FeatureSwitch("自动同步时间", features.autoTimeSync) {
                        features = features.copy(autoTimeSync = it)
                    }
                    FeatureSwitch("恢复前台时同步时间", features.syncOnResume) {
                        features = features.copy(syncOnResume = it)
                    }
                    Text("应用切到后台时", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !features.keepPowerOn,
                            onClick = { features = features.copy(keepPowerOn = false) },
                            label = { Text("自动暂停（省电）") },
                        )
                        FilterChip(
                            selected = features.keepPowerOn,
                            onClick = { features = features.copy(keepPowerOn = true) },
                            label = { Text("持续运行（挂机）") },
                        )
                    }
                    Text(
                        if (features.keepPowerOn) {
                            "应用切到后台或息屏后仍推进游戏，会保持 CPU 唤醒，耗电明显增加。"
                        } else {
                            "应用切到后台时冻结当前现场；返回应用立即从原位置继续，不需要读档。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedTextField(
                        value = overclockText,
                        onValueChange = { overclockText = it },
                        label = { Text("主频倍率") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = fastForwardText,
                        onValueChange = { fastForwardText = it },
                        label = { Text("快进上限（0 表示不限速）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        profile.copy(
                            name = name.trim(),
                            model = model,
                            icon = icon,
                            controlSceneId = controlSceneId,
                            features = features.copy(
                                overclockFactor = overclockText.toDoubleOrNull() ?: 1.0,
                                fastForwardLimit = fastForwardText.toIntOrNull() ?: 5,
                            ),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FeatureSwitch(
    label: String,
    checked: Boolean,
    supportingText: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label)
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun firmwareSummary(profile: LaunchProfile): String {
    val selected = buildList {
        if (profile.firmware.romUri != null) add("ROM")
        if (profile.firmware.norUri != null) add("NOR")
        if (profile.firmware.nandUri != null) add("NAND")
        if (profile.firmware.nand0Uri != null) add("NAND0")
        if (profile.firmware.stateUri != null) add("运行现场 STATE")
    }
    val errors = profile.validationErrors()
    return if (errors.isEmpty()) {
        "固件已就绪：${selected.joinToString()}"
    } else {
        "${selected.ifEmpty { listOf("尚未选择固件") }.joinToString()} · ${errors.first()}"
    }
}

private fun builtInIconGlyph(value: String): String = when (value) {
    "book" -> "📖"
    "game" -> "🎮"
    "chip" -> "▦"
    else -> "▣"
}
