package io.github.wangyu.nc2000.emulator

import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.wangyu.nc2000.controls.ControlScene
import io.github.wangyu.nc2000.controls.ControlSceneLayout
import io.github.wangyu.nc2000.controls.LcdFrameStyle
import io.github.wangyu.nc2000.controls.VirtualControl
import io.github.wangyu.nc2000.controls.VirtualControlActionKind
import io.github.wangyu.nc2000.controls.VirtualControlShape
import io.github.wangyu.nc2000.emulator.ui.EmulatorShortcutPad
import io.github.wangyu.nc2000.emulator.ui.EmulatorShortcutStrip
import io.github.wangyu.nc2000.emulator.ui.EmulatorTopBar
import io.github.wangyu.nc2000.emulator.ui.FullKeyboardLayout
import io.github.wangyu.nc2000.emulator.ui.FullKeyboardKeyPreview
import io.github.wangyu.nc2000.emulator.ui.MiniKeyboardLayout
import io.github.wangyu.nc2000.emulator.ui.RuntimeControlSheet
import io.github.wangyu.nc2000.emulator.ui.RuntimeLcdPalette
import io.github.wangyu.nc2000.launcher.FirmwareFiles
import io.github.wangyu.nc2000.nativebridge.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private const val LCD_WIDTH = 160
private const val LCD_HEIGHT = 80
private const val LCD_PIXELS = LCD_WIDTH * LCD_HEIGHT
private const val LCD_MAIN_WIDTH = LCD_WIDTH - 1
private const val CANDIDATE_COUNT = 9
private const val CHINESE_GLYPH_WIDTH = 16
private const val FIRST_CANDIDATE_CENTER = CHINESE_GLYPH_WIDTH + CHINESE_GLYPH_WIDTH / 2
private val LcdFrameContentGap = 3.dp

private enum class KeyboardMode {
    MINI,
    FULL,
    SCENE,
}

private enum class LoadTarget {
    NOR,
    STATE,
    QUICK,
}

private data class LcdFrame(
    val main: ImageBitmap?,
    val stripeLevels: ByteArray,
)

private val classicLcdColors = intArrayOf(
    0xffe8efcf.toInt(),
    0xffb1bd91.toInt(),
    0xff687354.toInt(),
    0xff172116.toInt(),
)

private val clearLcdColors = intArrayOf(
    0xfff2f5e8.toInt(),
    0xffb7c0a5.toInt(),
    0xff59634f.toInt(),
    0xff090d08.toInt(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen(
    title: String,
    profileId: String,
    firmware: FirmwareFiles,
    controlScene: ControlScene?,
    autoSaveFlash: Boolean,
    autoSaveState: Boolean,
    quickSaveFlash: Boolean,
    quickSaveState: Boolean,
    initialBackgroundContinues: Boolean,
    onBackgroundPolicyChange: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onStop: () -> Unit,
) {
    var stopping by remember { mutableStateOf(false) }
    var keyboardMode by rememberSaveable(controlScene?.id) {
        mutableStateOf(if (controlScene == null) KeyboardMode.MINI else KeyboardMode.SCENE)
    }
    var lcdPalette by rememberSaveable { mutableStateOf(RuntimeLcdPalette.CLASSIC) }
    var lcdFrameStyle by rememberSaveable(controlScene?.id) {
        mutableStateOf(controlScene?.lcdFrameStyle ?: LcdFrameStyle.SIMPLE)
    }
    var fastForward by rememberSaveable { mutableStateOf(false) }
    var fastForwardOverride by remember { mutableStateOf<Boolean?>(null) }
    var fastForwardMultiplier by remember { mutableStateOf<Double?>(null) }
    var showRuntimeMenu by remember { mutableStateOf(false) }
    var pendingLoad by remember { mutableStateOf<LoadTarget?>(null) }
    var runtimeMessage by remember { mutableStateOf<String?>(null) }
    var importName by remember { mutableStateOf<String?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importNameError by remember { mutableStateOf<String?>(null) }
    var importTempFile by remember { mutableStateOf<File?>(null) }
    var importTransform by remember { mutableStateOf<BinImportTransform?>(null) }
    var importStatus by remember { mutableStateOf(ImportStatus.parse(NativeBridge.importStatus())) }
    var backgroundContinues by rememberSaveable(title) {
        mutableStateOf(initialBackgroundContinues)
    }
    val lcdColors = if (lcdPalette == RuntimeLcdPalette.CLASSIC) classicLcdColors else clearLcdColors
    val physicalKeyboard = remember { PhysicalKeyboardInput(NativeBridge::setKey) }
    val virtualKeyboard = remember { VirtualKeyInput(NativeBridge::setKey) }
    val keyboardFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentBackgroundContinues by rememberUpdatedState(backgroundContinues)
    val fastForwardActive = fastForwardOverride ?: fastForward
    val setTemporaryFastForward: (Boolean) -> Unit = { requestedActive ->
        fastForwardOverride = requestedActive.takeIf { it != fastForward }
        NativeBridge.setFastForward(requestedActive)
    }
    val setFastForwardLocked: (Boolean) -> Unit = { locked ->
        fastForward = locked
        fastForwardOverride = null
        NativeBridge.setFastForward(locked)
    }
    val releaseAllInput = {
        physicalKeyboard.releaseAll()
        virtualKeyboard.releaseAll()
    }
    val openImportDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importUri = uri
            importName = displayName(context, uri)
            importNameError = null
        }
    }
    val openRuntimeMenu = {
        releaseAllInput()
        showRuntimeMenu = true
    }
    val requestQuickLoad = {
        releaseAllInput()
        pendingLoad = LoadTarget.QUICK
    }
    val moveToLauncher = {
        releaseAllInput()
        onBackground(currentBackgroundContinues)
    }

    LaunchedEffect(showRuntimeMenu) {
        if (showRuntimeMenu) releaseAllInput()
        else keyboardFocusRequester.requestFocus()
    }

    LaunchedEffect(fastForwardActive) {
        if (!fastForwardActive) {
            fastForwardMultiplier = null
            return@LaunchedEffect
        }
        while (true) {
            val measured = withContext(Dispatchers.IO) { NativeBridge.fastForwardMultiplier() }
            fastForwardMultiplier = measured.takeIf { it > 0.0 }
            delay(200)
        }
    }

    LaunchedEffect(importTempFile) {
        while (importTempFile != null) {
            val status = withContext(Dispatchers.IO) { ImportStatus.parse(NativeBridge.importStatus()) }
            importStatus = status
            if (status.busy) {
                runtimeMessage = when (status.state) {
                    "pending" -> "正在等待设备进入系统以开始导入…"
                    else -> "正在导入：${status.transferred}/${status.total} 字节"
                }
            }
            if (status.terminal) {
                importTempFile?.delete()
                importTempFile = null
                runtimeMessage = if (status.state == "succeeded") {
                    val preparation = when (importTransform) {
                        BinImportTransform.DECRYPTED -> "已解密并写入当前目录"
                        BinImportTransform.TRIMMED_ATTRIBUTE_HEADER -> "已去掉 48 字节属性头并写入当前目录"
                        else -> "已原样写入当前目录"
                    }
                    "$preparation；需保存 Flash 才能持久化"
                } else "导入失败：${status.error}"
                importTransform = null
                break
            }
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose { releaseAllInput() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) releaseAllInput()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = !stopping) {
        if (showRuntimeMenu) {
            showRuntimeMenu = false
        } else {
            moveToLauncher()
        }
    }

    LaunchedEffect(stopping) {
        if (!stopping) return@LaunchedEffect
        withContext(Dispatchers.IO) { NativeBridge.stop() }
        onStop()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(keyboardFocusRequester)
            .onFocusChanged {
                if (!it.hasFocus) releaseAllInput()
            }
            .onPreviewKeyEvent { event ->
                if (importStatus.busy) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> physicalKeyboard.handle(
                        event.nativeKeyEvent.keyCode,
                        true,
                    )
                    KeyEventType.KeyUp -> physicalKeyboard.handle(
                        event.nativeKeyEvent.keyCode,
                        false,
                    )
                    else -> false
                }
            }
            .focusable(),
        topBar = {
            EmulatorTopBar(
                title = title,
                onBack = {
                    moveToLauncher()
                },
                onOpenMenu = openRuntimeMenu,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val landscape = maxWidth > maxHeight
                if (keyboardMode == KeyboardMode.SCENE && controlScene != null) {
                    ControlSceneSurface(
                        scene = controlScene,
                        landscape = landscape,
                        lcdColors = lcdColors,
                        lcdFrameStyle = lcdFrameStyle,
                        enabled = !stopping && !importStatus.busy,
                        fastForwardLocked = fastForward,
                        fastForwardActive = fastForwardActive,
                        fastForwardMultiplier = fastForwardMultiplier,
                        onFastForwardPressedChange = setTemporaryFastForward,
                        onFastForwardLockedChange = setFastForwardLocked,
                        onKeyPressedChange = virtualKeyboard::setKey,
                        onOpenRuntimeMenu = openRuntimeMenu,
                    )
                } else if (landscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1.15f)) {
                            LcdDisplay(lcdColors, lcdFrameStyle)
                        }
                        KeyboardPanel(
                            mode = keyboardMode,
                            enabled = !stopping && !importStatus.busy,
                            fastForwardLocked = fastForward,
                            fastForwardActive = fastForwardActive,
                            fastForwardMultiplier = fastForwardMultiplier,
                            quickSaveTarget = storageSelectionLabel(quickSaveFlash, quickSaveState),
                            onFastForwardPressedChange = setTemporaryFastForward,
                            onFastForwardLockedChange = setFastForwardLocked,
                            onSave = {
                                NativeBridge.requestSave(quickSaveFlash, quickSaveState)
                                runtimeMessage = quickSaveMessage(quickSaveFlash, quickSaveState)
                            },
                            onLoad = requestQuickLoad,
                            onOpenRuntimeMenu = openRuntimeMenu,
                            onKeyPressedChange = virtualKeyboard::setKey,
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LcdDisplay(lcdColors, lcdFrameStyle)
                        Spacer(Modifier.size(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            KeyboardPanel(
                                mode = keyboardMode,
                                enabled = !stopping && !importStatus.busy,
                                fastForwardLocked = fastForward,
                                fastForwardActive = fastForwardActive,
                                fastForwardMultiplier = fastForwardMultiplier,
                                quickSaveTarget = storageSelectionLabel(quickSaveFlash, quickSaveState),
                                onFastForwardPressedChange = setTemporaryFastForward,
                                onFastForwardLockedChange = setFastForwardLocked,
                                onSave = {
                                    NativeBridge.requestSave(quickSaveFlash, quickSaveState)
                                    runtimeMessage = quickSaveMessage(quickSaveFlash, quickSaveState)
                                },
                                onLoad = requestQuickLoad,
                                onOpenRuntimeMenu = openRuntimeMenu,
                                onKeyPressedChange = virtualKeyboard::setKey,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
                if (stopping) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier.background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.shapes.large,
                            ).padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.sizeIn(maxWidth = 22.dp, maxHeight = 22.dp),
                            )
                            Text("正在结束模拟器并返回启动器…")
                        }
                    }
                }
                if (importStatus.busy && !stopping) {
                    ImportProgressOverlay(importStatus)
                }
            }
            Spacer(Modifier.size(6.dp))
            KeyboardModeSelector(
                mode = keyboardMode,
                hasScene = controlScene != null,
                onModeChange = {
                    virtualKeyboard.releaseAll()
                    keyboardMode = it
                },
            )
        }
    }

    if (showRuntimeMenu) {
        RuntimeControlSheet(
            profileId = profileId,
            firmware = firmware,
            palette = lcdPalette,
            frameStyle = lcdFrameStyle,
            fastForward = fastForward,
            fastForwardMultiplier = fastForwardMultiplier,
            message = runtimeMessage,
            onPaletteChange = {
                lcdPalette = it
                runtimeMessage = null
            },
            onFrameStyleChange = {
                lcdFrameStyle = it
                runtimeMessage = null
            },
            onFastForwardChange = {
                setFastForwardLocked(it)
                runtimeMessage = if (it) {
                    "加速已开启"
                } else {
                    "加速已关闭"
                }
            },
            onReset = {
                NativeBridge.requestReset()
                runtimeMessage = "已请求复位"
            },
            onSaveNor = {
                NativeBridge.requestSave(includeFlash = true, includeState = false)
                runtimeMessage = "正在保存 NOR 持久存储"
            },
            onLoadNor = {
                releaseAllInput()
                pendingLoad = LoadTarget.NOR
            },
            onSaveState = {
                NativeBridge.requestSave(includeFlash = false, includeState = true)
                runtimeMessage = "正在保存 STATE（RAM/CPU/外设现场）"
            },
            onLoadState = {
                releaseAllInput()
                pendingLoad = LoadTarget.STATE
            },
            onImport = {
                releaseAllInput()
                openImportDocument.launch(arrayOf("*/*"))
            },
            importBusy = importStatus.busy,
            backgroundContinues = backgroundContinues,
            onBackgroundContinuesChange = {
                backgroundContinues = it
                onBackgroundPolicyChange(it)
                runtimeMessage = if (it) {
                    "已选择后台持续运行；息屏后仍推进游戏，但耗电较高"
                } else {
                    "已选择后台自动暂停；返回应用会从当前现场继续"
                }
            },
            autoSaveFlash = autoSaveFlash,
            autoSaveState = autoSaveState,
            onBackground = {
                showRuntimeMenu = false
                moveToLauncher()
            },
            onStop = {
                showRuntimeMenu = false
                releaseAllInput()
                stopping = true
            },
            onDismiss = { showRuntimeMenu = false },
        )
    }

    pendingLoad?.let { target ->
        val includeFlash = target == LoadTarget.NOR ||
            (target == LoadTarget.QUICK && quickSaveFlash)
        val includeState = target == LoadTarget.STATE ||
            (target == LoadTarget.QUICK && quickSaveState)
        val targetLabel = storageSelectionLabel(includeFlash, includeState)
        AlertDialog(
            onDismissRequest = { pendingLoad = null },
            title = { Text("读取$targetLabel？") },
            text = {
                Text(
                    when {
                        includeFlash && includeState ->
                            "将重新读取 NOR 持久存储和 STATE 运行现场，丢弃两者在存档后产生的未保存变化。"
                        includeFlash ->
                            "将重新读取 NOR 持久存储，丢弃上次保存后产生的存储变化；当前 STATE 运行现场保持不变。"
                        else ->
                            "将恢复 STATE 中的 RAM、CPU 和外设现场，相当于回到保存时的不断电状态；NOR 保持不变。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLoad = null
                        runtimeMessage = if (NativeBridge.requestLoad(includeFlash, includeState)) {
                            "正在读取$targetLabel"
                        } else {
                            "还没有可读取的$targetLabel，请先保存"
                        }
                    },
                ) { Text("读取") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLoad = null }) { Text("取消") }
            },
        )
    }

    importName?.let { defaultName ->
        var editableName by remember(defaultName) { mutableStateOf(defaultName) }
        var importMode by remember(defaultName) { mutableStateOf(BinImportMode.AUTO) }
        AlertDialog(
            onDismissRequest = { importName = null; importUri = null; importNameError = null },
            title = { Text("导入到当前目录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = editableName, onValueChange = { editableName = it },
                        label = { Text("设备文件名（GBK，最多 16 字节）") }, singleLine = true,
                        isError = importNameError != null,
                        supportingText = if (importNameError != null) {
                            { Text(importNameError.orEmpty()) }
                        } else {
                            null
                        },
                    )
                    ImportModeOption(
                        selected = importMode == BinImportMode.AUTO,
                        title = "自动处理 BIN（推荐）",
                        description = "识别已解密文件或 Application 属性头，否则尝试解密",
                        onClick = { importMode = BinImportMode.AUTO },
                    )
                    ImportModeOption(
                        selected = importMode == BinImportMode.DIRECT,
                        title = "原样导入",
                        description = "不解密，也不去掉前 48 字节",
                        onClick = { importMode = BinImportMode.DIRECT },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val encoded = encodeDeviceFileName(editableName)
                    if (encoded.isFailure) {
                        importNameError = encoded.exceptionOrNull()?.message
                        return@TextButton
                    }
                    val uri = importUri ?: return@TextButton
                    importName = null
                    importUri = null
                    scope.launch {
                        val prepared = withContext(Dispatchers.IO) {
                            runCatching { prepareImportToCache(context, uri, importMode) }
                        }.getOrElse { error ->
                            runtimeMessage = error.message ?: "无法准备导入文件"
                            return@launch
                        }
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                NativeBridge.startImport(prepared.file.absolutePath, encoded.getOrThrow())
                            }
                        }
                        val error = result.fold({ it }, { it.message ?: "模拟器服务连接失败" })
                        if (error != null) {
                            prepared.file.delete()
                            runtimeMessage = "无法开始导入：$error"
                        } else {
                            importTempFile = prepared.file
                            importTransform = prepared.transform
                            importStatus = ImportStatus("pending", 0, prepared.file.length(), "")
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { importName = null; importUri = null; importNameError = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ImportModeOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(top = 8.dp, end = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportProgressOverlay(status: ImportStatus) {
    val fraction = if (status.total > 0L) {
        (status.transferred.toFloat() / status.total.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.large,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                if (status.state == "pending") "正在等待设备进入系统…" else "正在下载到模拟器当前目录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${(fraction * 100).roundToInt()}% · ${formatByteCount(status.transferred)} / ${formatByteCount(status.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (status.state == "pending") {
                    "开始传输后将自动开启最大加速"
                } else {
                    "已自动开启最大加速；完成后恢复原加速设置"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatByteCount(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MiB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KiB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun displayName(context: Context, uri: Uri): String = context.contentResolver.query(
    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    ?: uri.lastPathSegment ?: "import"

@Composable
private fun KeyboardModeSelector(
    mode: KeyboardMode,
    hasScene: Boolean,
    onModeChange: (KeyboardMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        FilterChip(
            selected = mode == KeyboardMode.MINI,
            onClick = { onModeChange(KeyboardMode.MINI) },
            label = { Text("精简") },
        )
        FilterChip(
            selected = mode == KeyboardMode.FULL,
            onClick = { onModeChange(KeyboardMode.FULL) },
            label = { Text("全键") },
        )
        if (hasScene) {
            FilterChip(
                selected = mode == KeyboardMode.SCENE,
                onClick = { onModeChange(KeyboardMode.SCENE) },
                label = { Text("游戏") },
            )
        }
    }
}

@Composable
private fun ControlSceneSurface(
    scene: ControlScene,
    landscape: Boolean,
    lcdColors: IntArray,
    lcdFrameStyle: LcdFrameStyle,
    enabled: Boolean,
    fastForwardLocked: Boolean,
    fastForwardActive: Boolean,
    fastForwardMultiplier: Double?,
    onFastForwardPressedChange: (Boolean) -> Unit,
    onFastForwardLockedChange: (Boolean) -> Unit,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
    onOpenRuntimeMenu: () -> Unit,
) {
    val layout = if (landscape) scene.landscape else scene.portrait
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(if (landscape) Alignment.Center else Alignment.TopCenter)
                .fillMaxWidth(if (landscape) 0.62f else 1f),
        ) {
            LcdDisplay(lcdColors, lcdFrameStyle)
        }
        SceneControls(
            layout = layout,
            canvasWidth = maxWidth,
            canvasHeight = maxHeight,
            enabled = enabled,
            fastForwardLocked = fastForwardLocked,
            fastForwardActive = fastForwardActive,
            fastForwardMultiplier = fastForwardMultiplier,
            onFastForwardPressedChange = onFastForwardPressedChange,
            onFastForwardLockedChange = onFastForwardLockedChange,
            onKeyPressedChange = onKeyPressedChange,
            onOpenRuntimeMenu = onOpenRuntimeMenu,
        )
    }
}

@Composable
private fun SceneControls(
    layout: ControlSceneLayout,
    canvasWidth: androidx.compose.ui.unit.Dp,
    canvasHeight: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    fastForwardLocked: Boolean,
    fastForwardActive: Boolean,
    fastForwardMultiplier: Double?,
    onFastForwardPressedChange: (Boolean) -> Unit,
    onFastForwardLockedChange: (Boolean) -> Unit,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
    onOpenRuntimeMenu: () -> Unit,
) {
    layout.controls.forEach { control ->
        val usesKeyboardVisual = control.action.kind == VirtualControlActionKind.KEY ||
            control.action.kind == VirtualControlActionKind.KEY_COMBINATION
        val shape = when (control.shape) {
            VirtualControlShape.CIRCLE -> CircleShape
            VirtualControlShape.RECTANGLE -> RoundedCornerShape(12.dp)
        }
        Box(
            modifier = Modifier
                .offset(
                    x = canvasWidth * control.x,
                    y = canvasHeight * control.y,
                )
                .size(
                    width = canvasWidth * control.width,
                    height = canvasHeight * control.height,
                )
                .then(
                    if (usesKeyboardVisual) Modifier else Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = if (enabled) control.opacity else control.opacity * 0.45f,
                            ),
                            shape = shape,
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = control.opacity),
                            shape = shape,
                        ),
                )
                .sceneControlGesture(
                    control = control,
                    enabled = enabled,
                    fastForwardLocked = fastForwardLocked,
                    onFastForwardPressedChange = onFastForwardPressedChange,
                    onFastForwardLockedChange = onFastForwardLockedChange,
                    onKeyPressedChange = onKeyPressedChange,
                    onOpenRuntimeMenu = onOpenRuntimeMenu,
                )
                .padding(if (usesKeyboardVisual) 1.dp else 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (usesKeyboardVisual) {
                Row(
                    modifier = Modifier.fillMaxSize().alpha(
                        if (enabled) control.opacity else control.opacity * 0.45f,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    control.action.keyIds.forEach { keyId ->
                        FullKeyboardKeyPreview(keyId, Modifier.weight(1f).fillMaxSize())
                    }
                }
            } else {
                Text(
                    text = if (
                        control.action.kind == VirtualControlActionKind.HOLD_FAST_FORWARD ||
                        control.action.kind == VirtualControlActionKind.TOGGLE_FAST_FORWARD
                    ) {
                        fastForwardMultiplier?.let(::fastForwardMultiplierText)
                            ?: if (fastForwardActive) "加速中" else control.label
                    } else {
                        control.label
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun Modifier.sceneControlGesture(
    control: VirtualControl,
    enabled: Boolean,
    fastForwardLocked: Boolean,
    onFastForwardPressedChange: (Boolean) -> Unit,
    onFastForwardLockedChange: (Boolean) -> Unit,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
    onOpenRuntimeMenu: () -> Unit,
): Modifier = pointerInput(control, enabled, fastForwardLocked, onFastForwardPressedChange) {
    if (!enabled) return@pointerInput
    when (control.action.kind) {
        VirtualControlActionKind.KEY,
        VirtualControlActionKind.KEY_COMBINATION,
        -> detectTapGestures(
            onPress = {
                control.action.keyIds.forEach { onKeyPressedChange(it, true) }
                try {
                    tryAwaitRelease()
                } finally {
                    control.action.keyIds.asReversed().forEach { onKeyPressedChange(it, false) }
                }
            },
        )

        VirtualControlActionKind.HOLD_FAST_FORWARD -> {
            var lockedBeforeGesture = false
            var longPressTriggered = false
            detectTapGestures(
                onPress = {
                    lockedBeforeGesture = fastForwardLocked
                    longPressTriggered = false
                    try {
                        tryAwaitRelease()
                    } finally {
                        if (longPressTriggered) {
                            onFastForwardPressedChange(lockedBeforeGesture)
                        }
                    }
                },
                onLongPress = {
                    longPressTriggered = true
                    onFastForwardPressedChange(!lockedBeforeGesture)
                },
                onTap = { onFastForwardLockedChange(!lockedBeforeGesture) },
            )
        }

        VirtualControlActionKind.TOGGLE_FAST_FORWARD -> detectTapGestures(
            onTap = { onFastForwardLockedChange(!fastForwardLocked) },
        )

        VirtualControlActionKind.OPEN_RUNTIME_MENU -> detectTapGestures(
            onTap = { onOpenRuntimeMenu() },
        )
    }
}

@Composable
private fun LcdDisplay(
    lcdColors: IntArray,
    frameStyle: LcdFrameStyle,
) {
    val context = LocalContext.current
    val levels = remember { ByteArray(LCD_PIXELS) }
    var sequence by remember { mutableLongStateOf(0L) }
    var renderedFrame by remember {
        mutableStateOf(LcdFrame(main = null, stripeLevels = ByteArray(LCD_HEIGHT)))
    }
    val stripeAssets = remember(context) {
        runCatching { LcdStripeAssets.load(context) }.getOrNull()
    }

    LaunchedEffect(lcdColors) {
        while (true) {
            withFrameNanos { }
            val lastSequence = sequence
            val nextFrame = withContext(Dispatchers.Default) {
                val nextSequence = NativeBridge.copyLcdFrame(levels, lastSequence)
                if (nextSequence == 0L || nextSequence == lastSequence) return@withContext null

                val nextStripeLevels = ByteArray(LCD_HEIGHT)
                val colors = IntArray(LCD_MAIN_WIDTH * LCD_HEIGHT)
                for (row in 0 until LCD_HEIGHT) {
                    nextStripeLevels[row] = levels[row * LCD_WIDTH]
                    for (column in 1 until LCD_WIDTH) {
                        colors[row * LCD_MAIN_WIDTH + column - 1] =
                            lcdColors[levels[row * LCD_WIDTH + column].toInt().coerceIn(0, 3)]
                    }
                }
                val mainFrame = Bitmap.createBitmap(
                    colors,
                    LCD_MAIN_WIDTH,
                    LCD_HEIGHT,
                    Bitmap.Config.ARGB_8888,
                ).asImageBitmap()
                nextSequence to LcdFrame(main = mainFrame, stripeLevels = nextStripeLevels)
            }
            nextFrame?.let { (nextSequence, frame) ->
                renderedFrame = frame
                sequence = nextSequence
            }
        }
    }

    if (frameStyle == LcdFrameStyle.CLASSIC_BEZEL) {
        ClassicLcdBezel(candidateMarkerFractions(stripeAssets?.layout)) {
            LcdPanel(
                lcdColors = lcdColors,
                mainFrame = renderedFrame.main,
                stripeLevels = renderedFrame.stripeLevels,
                stripeAssets = stripeAssets,
                modifier = Modifier.fillMaxWidth(),
                borderWidth = 1.dp,
            )
        }
    } else {
        LcdPanel(
            lcdColors = lcdColors,
            mainFrame = renderedFrame.main,
            stripeLevels = renderedFrame.stripeLevels,
            stripeAssets = stripeAssets,
            modifier = Modifier.fillMaxWidth().sizeIn(maxWidth = 720.dp),
            borderWidth = 1.dp,
        )
    }
}

@Composable
private fun LcdPanel(
    lcdColors: IntArray,
    mainFrame: ImageBitmap?,
    stripeLevels: ByteArray,
    stripeAssets: LcdStripeAssets?,
    modifier: Modifier,
    borderWidth: Dp,
) {
    if (stripeAssets == null) {
        Box(
            modifier = modifier
                .border(borderWidth, Color(0xff33372f))
                .background(Color(lcdColors[0]))
                .padding(LcdFrameContentGap),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .background(Color(lcdColors[0])),
                contentAlignment = Alignment.Center,
            ) {
                mainFrame?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "文曲星 LCD 屏幕",
                        modifier = Modifier.fillMaxSize(),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }
    } else {
        val layout = stripeAssets.layout
        val darkTint = ColorFilter.tint(Color(lcdColors[3]), BlendMode.SrcIn)
        Box(
            modifier = modifier
                .border(borderWidth, Color(0xff33372f))
                .background(Color(lcdColors[0]))
                .padding(LcdFrameContentGap),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(layout.width.toFloat() / layout.height)
                    .background(Color(lcdColors[0])),
            ) {
                scale(
                    scaleX = size.width / layout.width,
                    scaleY = size.height / layout.height,
                    pivot = Offset.Zero,
                ) {
                    drawImage(
                        image = stripeAssets.texture,
                        srcOffset = IntOffset(layout.background.x, layout.background.y),
                        srcSize = IntSize(layout.background.width, layout.background.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(layout.width, layout.height),
                        colorFilter = darkTint,
                        filterQuality = FilterQuality.None,
                    )
                    layout.stripes.forEachIndexed { index, stripe ->
                        val level = stripeLevels[index].toInt().coerceIn(0, 3)
                        if (level == 0) return@forEachIndexed
                        drawImage(
                            image = stripeAssets.texture,
                            srcOffset = IntOffset(stripe.source.x, stripe.source.y),
                            srcSize = IntSize(stripe.source.width, stripe.source.height),
                            dstOffset = IntOffset(stripe.left, stripe.top),
                            dstSize = IntSize(stripe.source.width, stripe.source.height),
                            alpha = level / 3f,
                            colorFilter = darkTint,
                            filterQuality = FilterQuality.None,
                        )
                    }
                    mainFrame?.let { frame ->
                        drawImage(
                            image = frame,
                            dstOffset = IntOffset(layout.mainLeft, layout.mainTop),
                            dstSize = IntSize(
                                LCD_MAIN_WIDTH * layout.pixelWidth,
                                LCD_HEIGHT * layout.pixelHeight,
                            ),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicLcdBezel(
    candidateFractions: List<Float>,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = 760.dp)
            .clip(shape)
            .background(Color(0xffc9c8c0))
            .drawBehind {
                val step = 12.dp.toPx()
                var start = -size.height
                while (start < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(start, size.height),
                        end = Offset(start + size.height, 0f),
                        strokeWidth = 2.dp.toPx(),
                    )
                    start += step
                }
            }
            .border(2.dp, Color(0xfff2f0e7), shape)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xff585b55), RoundedCornerShape(4.dp))
                .padding(3.dp),
        ) {
            content()
        }
        Spacer(Modifier.size(5.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp).sizeIn(minHeight = 17.dp),
        ) {
            candidateFractions.forEachIndexed { index, fraction ->
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * fraction - 8.5.dp)
                        .size(17.dp)
                        .background(Color(0xff5a5b56), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (index + 1).toString(),
                        color = Color(0xfff4f2e9),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun candidateMarkerFractions(layout: LcdStripeLayout?): List<Float> {
    val totalWidth = layout?.width?.toFloat() ?: LCD_WIDTH.toFloat()
    val mainLeft = layout?.mainLeft?.toFloat() ?: 1f
    val pixelWidth = layout?.pixelWidth?.toFloat() ?: 1f
    return List(CANDIDATE_COUNT) { index ->
        val logicalCenter = FIRST_CANDIDATE_CENTER + index * CHINESE_GLYPH_WIDTH
        (mainLeft + logicalCenter * pixelWidth) / totalWidth
    }
}

@Composable
private fun KeyboardPanel(
    mode: KeyboardMode,
    enabled: Boolean,
    fastForwardLocked: Boolean,
    fastForwardActive: Boolean,
    fastForwardMultiplier: Double?,
    quickSaveTarget: String,
    onFastForwardPressedChange: (Boolean) -> Unit,
    onFastForwardLockedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onOpenRuntimeMenu: () -> Unit,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().sizeIn(maxWidth = 620.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (mode == KeyboardMode.MINI) {
            MiniKeyboard(
                enabled = enabled,
                fastForwardLocked = fastForwardLocked,
                fastForwardActive = fastForwardActive,
                fastForwardMultiplier = fastForwardMultiplier,
                quickSaveTarget = quickSaveTarget,
                onFastForwardPressedChange = onFastForwardPressedChange,
                onFastForwardLockedChange = onFastForwardLockedChange,
                onSave = onSave,
                onLoad = onLoad,
                onOpenRuntimeMenu = onOpenRuntimeMenu,
                onKeyPressedChange = onKeyPressedChange,
            )
        } else {
            FullKeyboard(
                enabled = enabled,
                fastForwardLocked = fastForwardLocked,
                fastForwardActive = fastForwardActive,
                fastForwardMultiplier = fastForwardMultiplier,
                quickSaveTarget = quickSaveTarget,
                onFastForwardPressedChange = onFastForwardPressedChange,
                onFastForwardLockedChange = onFastForwardLockedChange,
                onSave = onSave,
                onLoad = onLoad,
                onOpenRuntimeMenu = onOpenRuntimeMenu,
                onKeyPressedChange = onKeyPressedChange,
            )
        }
    }
}

@Composable
private fun MiniKeyboard(
    enabled: Boolean,
    fastForwardLocked: Boolean,
    fastForwardActive: Boolean,
    fastForwardMultiplier: Double?,
    quickSaveTarget: String,
    onFastForwardPressedChange: (Boolean) -> Unit,
    onFastForwardLockedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onOpenRuntimeMenu: () -> Unit,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
) {
    MiniKeyboardLayout(
        enabled = enabled,
        onKeyPressedChange = onKeyPressedChange,
        shortcutContent = {
            EmulatorShortcutStrip(
                enabled = enabled,
                fastForwardLocked = fastForwardLocked,
                fastForwardActive = fastForwardActive,
                fastForwardMultiplier = fastForwardMultiplier,
                quickSaveTarget = quickSaveTarget,
                onFastForwardPressedChange = onFastForwardPressedChange,
                onFastForwardLockedChange = onFastForwardLockedChange,
                onSave = onSave,
                onLoad = onLoad,
                onOpenMenu = onOpenRuntimeMenu,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun FullKeyboard(
    enabled: Boolean,
    fastForwardLocked: Boolean,
    fastForwardActive: Boolean,
    fastForwardMultiplier: Double?,
    quickSaveTarget: String,
    onFastForwardPressedChange: (Boolean) -> Unit,
    onFastForwardLockedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onOpenRuntimeMenu: () -> Unit,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
) {
    FullKeyboardLayout(
        enabled = enabled,
        onKeyPressedChange = onKeyPressedChange,
        shortcutContent = {
            EmulatorShortcutPad(
                enabled = enabled,
                fastForwardLocked = fastForwardLocked,
                fastForwardActive = fastForwardActive,
                fastForwardMultiplier = fastForwardMultiplier,
                quickSaveTarget = quickSaveTarget,
                onFastForwardPressedChange = onFastForwardPressedChange,
                onFastForwardLockedChange = onFastForwardLockedChange,
                onSave = onSave,
                onLoad = onLoad,
                onOpenMenu = onOpenRuntimeMenu,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

private fun storageSelectionLabel(includeFlash: Boolean, includeState: Boolean): String = when {
    includeFlash && includeState -> "NOR + STATE"
    includeFlash -> "NOR"
    else -> "STATE"
}

private fun quickSaveMessage(includeFlash: Boolean, includeState: Boolean): String =
    "正在快捷保存${storageSelectionLabel(includeFlash, includeState)}"

private fun fastForwardMultiplierText(multiplier: Double): String {
    val rounded = (multiplier * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "×${rounded.toInt()}" else "×$rounded"
}
