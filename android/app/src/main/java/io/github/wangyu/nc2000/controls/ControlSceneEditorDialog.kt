package io.github.wangyu.nc2000.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.wangyu.nc2000.emulator.ui.FullKeyboardKeyPreview
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlSceneEditorDialog(
    scenes: List<ControlScene>,
    onSave: (ControlScene) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember {
        mutableStateOf(scenes.firstOrNull() ?: newControlScene())
    }
    var portrait by remember(draft.id) { mutableStateOf(true) }
    var selectedControlId by remember(draft.id, portrait) { mutableStateOf<String?>(null) }
    var keyPickerControlId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val layout = if (portrait) draft.portrait else draft.landscape
    val selectedControl = layout.controls.firstOrNull { it.id == selectedControlId }
    val errors = draft.validationErrors()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "控制场景编辑器",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    scenes.forEach { scene ->
                        FilterChip(
                            selected = draft.id == scene.id,
                            onClick = {
                                draft = scene
                                selectedControlId = null
                            },
                            label = { Text(scene.name) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val scene = newControlScene()
                            draft = scene
                            selectedControlId = null
                        },
                    ) { Text("新建") }
                    TextButton(
                        onClick = {
                            val scene = draft.copy(
                                id = UUID.randomUUID().toString(),
                                name = "${draft.name} 副本",
                            )
                            draft = scene
                            selectedControlId = null
                        },
                    ) { Text("复制") }
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = scenes.any { it.id == draft.id },
                    ) { Text("删除") }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { draft = draft.copy(name = it) },
                            label = { Text("场景名称") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = portrait,
                                onClick = {
                                    portrait = true
                                    selectedControlId = null
                                },
                                label = { Text("竖屏布局") },
                            )
                            FilterChip(
                                selected = !portrait,
                                onClick = {
                                    portrait = false
                                    selectedControlId = null
                                },
                                label = { Text("横屏布局") },
                            )
                        }
                    }
                    item {
                        Text("LCD 边框", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = draft.lcdFrameStyle == LcdFrameStyle.SIMPLE,
                                onClick = {
                                    draft = draft.copy(lcdFrameStyle = LcdFrameStyle.SIMPLE)
                                },
                                label = { Text("简洁") },
                            )
                            FilterChip(
                                selected = draft.lcdFrameStyle == LcdFrameStyle.CLASSIC_BEZEL,
                                onClick = {
                                    draft = draft.copy(lcdFrameStyle = LcdFrameStyle.CLASSIC_BEZEL)
                                },
                                label = { Text("经典机身") },
                            )
                        }
                    }
                    item {
                        SceneEditorCanvas(
                            layout = layout,
                            portrait = portrait,
                            lcdFrameStyle = draft.lcdFrameStyle,
                            selectedControlId = selectedControlId,
                            onSelectControl = { selectedControlId = it },
                            onControlChange = { updated ->
                                draft = draft.withLayout(
                                    portrait = portrait,
                                    layout = layout.replace(updated),
                                )
                            },
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                val control = VirtualControl(
                                    label = "输入",
                                    x = 0.35f,
                                    y = 0.72f,
                                    width = 0.30f,
                                    height = 0.10f,
                                    action = VirtualControlAction(
                                        VirtualControlActionKind.KEY,
                                        listOf(0x1d),
                                    ),
                                )
                                draft = draft.withLayout(
                                    portrait = portrait,
                                    layout = layout.copy(controls = layout.controls + control),
                                )
                                selectedControlId = control.id
                            },
                        ) { Text("添加虚拟按键") }
                    }
                    selectedControl?.let { control ->
                        item {
                            ControlProperties(
                                control = control,
                                onChange = { updated ->
                                    draft = draft.withLayout(
                                        portrait = portrait,
                                        layout = layout.replace(updated),
                                    )
                                },
                                onPickKeys = { keyPickerControlId = control.id },
                                onDelete = {
                                    draft = draft.withLayout(
                                        portrait = portrait,
                                        layout = layout.copy(
                                            controls = layout.controls.filterNot { it.id == control.id },
                                        ),
                                    )
                                    selectedControlId = null
                                },
                            )
                        }
                    }
                    if (errors.isNotEmpty()) {
                        item {
                            Text(
                                errors.first(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        enabled = errors.isEmpty(),
                        onClick = {
                            onSave(draft)
                        },
                    ) { Text("保存场景") }
                }
            }
        }
    }

    keyPickerControlId?.let { controlId ->
        val control = layout.controls.firstOrNull { it.id == controlId }
        if (control != null) {
            KeySelectionDialog(
                multiple = control.action.kind == VirtualControlActionKind.KEY_COMBINATION,
                selectedIds = control.action.keyIds,
                onConfirm = { keyIds ->
                    draft = draft.withLayout(
                        portrait = portrait,
                        layout = layout.replace(
                            control.copy(action = control.action.copy(keyIds = keyIds)),
                        ),
                    )
                    keyPickerControlId = null
                },
                onDismiss = { keyPickerControlId = null },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除控制场景？") },
            text = { Text("引用此场景的启动入口会自动改用默认键盘。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(draft.id)
                        val next = scenes.firstOrNull { it.id != draft.id }
                        draft = next ?: newControlScene()
                        selectedControlId = null
                        confirmDelete = false
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SceneEditorCanvas(
    layout: ControlSceneLayout,
    portrait: Boolean,
    lcdFrameStyle: LcdFrameStyle,
    selectedControlId: String?,
    onSelectControl: (String) -> Unit,
    onControlChange: (VirtualControl) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = if (portrait) 420.dp else 720.dp)
            .aspectRatio(if (portrait) 0.60f else 2f)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        val density = LocalDensity.current
        val canvasWidthPx = with(density) { maxWidth.toPx() }
        val canvasHeightPx = with(density) { maxHeight.toPx() }
        if (lcdFrameStyle == LcdFrameStyle.CLASSIC_BEZEL) {
            Column(
                modifier = Modifier
                    .align(if (portrait) Alignment.TopCenter else Alignment.Center)
                    .fillMaxWidth(if (portrait) 0.96f else 0.64f)
                    .background(Color(0xffd6d4ca), RoundedCornerShape(5.dp))
                    .border(2.dp, Color(0xfff3f0e5), RoundedCornerShape(5.dp))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PreviewLcdPanel()
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(16.dp),
                ) {
                    previewCandidateFractions.forEachIndexed { index, fraction ->
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.offset(x = maxWidth * fraction - 4.dp),
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(if (portrait) Alignment.TopCenter else Alignment.Center)
                    .fillMaxWidth(if (portrait) 0.94f else 0.60f),
            ) {
                PreviewLcdPanel()
            }
        }

        layout.controls.forEach { control ->
            val currentControl by rememberUpdatedState(control)
            val usesKeyboardVisual = control.action.kind == VirtualControlActionKind.KEY ||
                control.action.kind == VirtualControlActionKind.KEY_COMBINATION
            val shape = if (control.shape == VirtualControlShape.CIRCLE) {
                CircleShape
            } else {
                RoundedCornerShape(10.dp)
            }
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * control.x, y = maxHeight * control.y)
                    .size(maxWidth * control.width, maxHeight * control.height)
                    .then(
                        if (usesKeyboardVisual) Modifier else Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = control.opacity),
                            shape,
                        ),
                    )
                    .border(
                        if (selectedControlId == control.id) 3.dp else 1.dp,
                        if (selectedControlId == control.id) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape,
                    )
                    .clickable { onSelectControl(control.id) }
                    .pointerInput(control.id, canvasWidthPx, canvasHeightPx) {
                        var dragX = control.x
                        var dragY = control.y
                        detectDragGestures(
                            onDragStart = {
                                dragX = currentControl.x
                                dragY = currentControl.y
                                onSelectControl(currentControl.id)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragX = (dragX + dragAmount.x / canvasWidthPx)
                                    .coerceIn(0f, 1f - currentControl.width)
                                dragY = (dragY + dragAmount.y / canvasHeightPx)
                                    .coerceIn(0f, 1f - currentControl.height)
                                onControlChange(
                                    currentControl.copy(
                                        x = dragX,
                                        y = dragY,
                                    ),
                                )
                            },
                        )
                    }
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (usesKeyboardVisual) {
                    Row(
                        modifier = Modifier.fillMaxSize().alpha(control.opacity),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        control.action.keyIds.forEach { keyId ->
                            FullKeyboardKeyPreview(keyId, Modifier.weight(1f).fillMaxSize())
                        }
                    }
                } else {
                    Text(
                        control.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewLcdPanel() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.35f)
            .background(Color(0xffdbe5bd), RoundedCornerShape(4.dp))
            .border(2.dp, Color(0xff4c5146), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("LCD", color = Color(0xff4c5146), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ControlProperties(
    control: VirtualControl,
    onChange: (VirtualControl) -> Unit,
    onPickKeys: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("虚拟按键设置", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = control.label,
            onValueChange = { onChange(control.copy(label = it)) },
            label = { Text("显示标签") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "按键外观取自全键键盘库；下面的形状和尺寸定义可触摸响应区域。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = control.shape == VirtualControlShape.RECTANGLE,
                onClick = { onChange(control.copy(shape = VirtualControlShape.RECTANGLE)) },
                label = { Text("矩形响应区") },
            )
            FilterChip(
                selected = control.shape == VirtualControlShape.CIRCLE,
                onClick = { onChange(control.copy(shape = VirtualControlShape.CIRCLE)) },
                label = { Text("圆形响应区") },
            )
        }
        SceneSlider("横向位置", control.x, 0f..(1f - control.width)) {
            onChange(control.copy(x = it))
        }
        SceneSlider("纵向位置", control.y, 0f..(1f - control.height)) {
            onChange(control.copy(y = it))
        }
        SceneSlider("宽度", control.width, 0.05f..(1f - control.x)) {
            onChange(control.copy(width = it))
        }
        SceneSlider("高度", control.height, 0.05f..(1f - control.y)) {
            onChange(control.copy(height = it))
        }
        SceneSlider("透明度", control.opacity, 0.15f..1f) {
            onChange(control.copy(opacity = it))
        }
        Text("按键行为", fontWeight = FontWeight.SemiBold)
        VirtualControlActionKind.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kind ->
                    FilterChip(
                        selected = control.action.kind == kind,
                        onClick = {
                            onChange(
                                control.copy(
                                    action = VirtualControlAction(
                                        kind = kind,
                                        keyIds = defaultKeyIds(kind, control.action.keyIds),
                                    ),
                                ),
                            )
                        },
                        label = { Text(actionLabel(kind)) },
                    )
                }
            }
        }
        if (
            control.action.kind == VirtualControlActionKind.KEY ||
            control.action.kind == VirtualControlActionKind.KEY_COMBINATION
        ) {
            Text(
                "映射：${control.action.keyIds.joinToString(" + ") { NcKeyCatalog.label(it) }}",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onPickKeys) { Text("选择按键") }
        }
        TextButton(onClick = onDelete) { Text("删除此虚拟按键") }
    }
}

@Composable
private fun SceneSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text("$label ${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
private fun KeySelectionDialog(
    multiple: Boolean,
    selectedIds: List<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(selectedIds, multiple) { mutableStateOf(selectedIds.toSet()) }
    val valid = if (multiple) selected.size >= 2 else selected.size == 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (multiple) "选择组合键" else "选择按键") },
        text = {
            LazyColumn(modifier = Modifier.sizeIn(maxHeight = 480.dp)) {
                items(NcKeyCatalog.all, key = { it.id }) { key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (multiple) {
                                    if (key.id in selected) selected - key.id else selected + key.id
                                } else {
                                    setOf(key.id)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = key.id in selected,
                            onCheckedChange = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        FullKeyboardKeyPreview(
                            keyId = key.id,
                            modifier = Modifier.width(76.dp).height(44.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${key.label} · 0x${key.id.toString(16).padStart(2, '0')}")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onConfirm(NcKeyCatalog.all.filter { it.id in selected }.map { it.id })
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun ControlScene.withLayout(
    portrait: Boolean,
    layout: ControlSceneLayout,
): ControlScene = if (portrait) copy(portrait = layout) else copy(landscape = layout)

private fun ControlSceneLayout.replace(control: VirtualControl): ControlSceneLayout = copy(
    controls = controls.map { if (it.id == control.id) control else it },
)

private fun newControlScene() = ControlScene(
    name = "新控制场景",
    portrait = ControlSceneLayout(),
    landscape = ControlSceneLayout(),
)

private fun defaultKeyIds(kind: VirtualControlActionKind, current: List<Int>): List<Int> = when (kind) {
    VirtualControlActionKind.KEY -> listOf(current.firstOrNull() ?: 0x1d)
    VirtualControlActionKind.KEY_COMBINATION -> if (current.size >= 2) current else listOf(0x1d, 0x3b)
    else -> emptyList()
}

private fun actionLabel(kind: VirtualControlActionKind): String = when (kind) {
    VirtualControlActionKind.KEY -> "单键"
    VirtualControlActionKind.KEY_COMBINATION -> "组合键"
    VirtualControlActionKind.HOLD_FAST_FORWARD -> "加速（单击锁定/长按临时）"
    VirtualControlActionKind.TOGGLE_FAST_FORWARD -> "锁定加速"
    VirtualControlActionKind.OPEN_RUNTIME_MENU -> "运行菜单"
}

private val previewCandidateFractions = List(9) { index ->
    val mainLeft = 105f
    val firstCandidateCenter = 24f
    val candidateWidth = 16f
    val pixelWidth = 5f
    (mainLeft + (firstCandidateCenter + index * candidateWidth) * pixelWidth) / 938f
}
