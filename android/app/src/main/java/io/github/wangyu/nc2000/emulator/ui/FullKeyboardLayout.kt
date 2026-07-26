package io.github.wangyu.nc2000.emulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wangyu.nc2000.ui.theme.NC2000Theme
import io.github.wangyu.nc2000.ui.theme.NcColorTokens

private const val FullKeyboardColumns = 10

internal data class EmulatorKeySpec(
    val label: String,
    val keyId: Int,
)

internal val fullKeyboardRows = listOf(
    listOf(
        EmulatorKeySpec("时间", 0x08),
        EmulatorKeySpec("资料", 0x09),
        EmulatorKeySpec("行程", 0x0a),
        EmulatorKeySpec("英汉", 0x0b),
        EmulatorKeySpec("名片", 0x0c),
        EmulatorKeySpec("计算", 0x0d),
        EmulatorKeySpec("网络", 0x0e),
        EmulatorKeySpec("开关", 0x0f),
    ),
    listOf(
        EmulatorKeySpec("F1", 0x10),
        EmulatorKeySpec("F2", 0x11),
        EmulatorKeySpec("F3", 0x12),
        EmulatorKeySpec("F4", 0x13),
        EmulatorKeySpec("报时", 0x14),
        EmulatorKeySpec("发音", 0x15),
    ),
    listOf(
        EmulatorKeySpec("Q", 0x20),
        EmulatorKeySpec("W", 0x21),
        EmulatorKeySpec("E", 0x22),
        EmulatorKeySpec("R", 0x23),
        EmulatorKeySpec("T / 7", 0x24),
        EmulatorKeySpec("Y / 8", 0x25),
        EmulatorKeySpec("U / 9", 0x26),
        EmulatorKeySpec("I", 0x27),
        EmulatorKeySpec("O", 0x18),
        EmulatorKeySpec("P", 0x1c),
    ),
    listOf(
        EmulatorKeySpec("A", 0x28),
        EmulatorKeySpec("S", 0x29),
        EmulatorKeySpec("D", 0x2a),
        EmulatorKeySpec("F", 0x2b),
        EmulatorKeySpec("G / 4", 0x2c),
        EmulatorKeySpec("H / 5", 0x2d),
        EmulatorKeySpec("J / 6", 0x2e),
        EmulatorKeySpec("K", 0x2f),
        EmulatorKeySpec("L", 0x19),
        EmulatorKeySpec("输入", 0x1d),
    ),
    listOf(
        EmulatorKeySpec("Z", 0x30),
        EmulatorKeySpec("X", 0x31),
        EmulatorKeySpec("C", 0x32),
        EmulatorKeySpec("V", 0x33),
        EmulatorKeySpec("B / 1", 0x34),
        EmulatorKeySpec("N / 2", 0x35),
        EmulatorKeySpec("M / 3", 0x36),
        EmulatorKeySpec("Pg↑", 0x37),
        EmulatorKeySpec("▲", 0x1a),
        EmulatorKeySpec("Pg↓", 0x1e),
    ),
    listOf(
        EmulatorKeySpec("求助", 0x38),
        EmulatorKeySpec("中英数", 0x39),
        EmulatorKeySpec("输入法", 0x3a),
        EmulatorKeySpec("跳出", 0x3b),
        EmulatorKeySpec("0", 0x3c),
        EmulatorKeySpec(".", 0x3d),
        EmulatorKeySpec("空格", 0x3e),
        EmulatorKeySpec("←", 0x3f),
        EmulatorKeySpec("▼", 0x1b),
        EmulatorKeySpec("→", 0x1f),
    ),
)

/**
 * Touch-only grouping for the full keyboard. The source rows above remain the
 * canonical NC key order; these groups only add breathing room between the
 * areas operated by each thumb.
 */
internal data class FullKeyboardTouchRow(
    val groups: List<List<EmulatorKeySpec>>,
)

internal val fullKeyboardTouchRows = fullKeyboardRows.drop(2).mapIndexed { index, row ->
    FullKeyboardTouchRow(
        groups = if (index == fullKeyboardRows.size - 3) {
            // Mode/system keys, central thumb keys, then navigation keys.
            listOf(row.take(4), row.slice(4..6), row.takeLast(3))
        } else {
            // QWERTY/calculator rows are split into balanced left/right halves.
            listOf(row.take(5), row.drop(5))
        },
    )
}

/** Compact mode keeps a physical D-pad and a separate action cluster. */
internal val miniDirectionRows: List<List<Int?>> = listOf(
    listOf(null, 0x1a, null),
    listOf(0x3f, null, 0x1f),
    listOf(null, 0x1b, null),
)

internal val miniActionRows: List<List<Int?>> = listOf(
    listOf(0x0f, 0x1d),
    listOf(0x3b, 0x3e),
    listOf(0x25, 0x35),
)

internal val fullKeyboardVisuals: Map<Int, KeyVisualSpec> = listOf(
    visual(0x08, "时间", tone = KeyVisualTone.Teal),
    visual(0x09, "资料", tone = KeyVisualTone.Teal),
    visual(0x0a, "行程", tone = KeyVisualTone.Teal),
    visual(0x0b, "英汉", tone = KeyVisualTone.Teal),
    visual(0x0c, "名片", tone = KeyVisualTone.Teal),
    visual(0x0d, "计算", tone = KeyVisualTone.Teal),
    visual(0x0e, "网络", tone = KeyVisualTone.Teal),
    visual(0x0f, "开关", tone = KeyVisualTone.Lime),
    visual(0x10, "F1"),
    visual(0x11, "F2"),
    visual(0x12, "F3"),
    visual(0x13, "F4"),
    visual(0x14, "报时", tone = KeyVisualTone.Rose),
    visual(0x15, "发音", tone = KeyVisualTone.Rose),
    visual(0x20, "Q", "sin", above = "sin⁻¹"),
    visual(0x21, "W", "cos", above = "cos⁻¹"),
    visual(0x22, "E", "tan", above = "tan⁻¹"),
    visual(0x23, "R", "1/x", above = "hyp"),
    visual(0x24, "T", "7"),
    visual(0x25, "Y", "8"),
    visual(0x26, "U", "9"),
    visual(0x27, "I", "%"),
    visual(0x18, "O", "÷", above = "*"),
    visual(0x1c, "P", "MC", above = "输入"),
    visual(0x28, "A", "log", above = "10ˣ"),
    visual(0x29, "S", "ln", above = "eˣ"),
    visual(0x2a, "D", "xʸ", above = "ʸ√x"),
    visual(0x2b, "F", "√", above = "x²"),
    visual(0x2c, "G", "4"),
    visual(0x2d, "H", "5"),
    visual(0x2e, "J", "6"),
    visual(0x2f, "K", "+/−"),
    visual(0x19, "L", "×"),
    visual(0x1d, "MR", above = "输入", tone = KeyVisualTone.Rose),
    visual(0x30, "Z", "(", above = ")"),
    visual(0x31, "X", "π", above = "x!"),
    visual(0x32, "C", "EXP", above = "0/±"),
    visual(0x33, "V", "c", accent = LegendColorRole.Yellow),
    visual(0x34, "B", "1"),
    visual(0x35, "N", "2"),
    visual(0x36, "M", "3"),
    visual(
        0x37,
        "⇞",
        "税",
        contentDescription = "上翻页",
        accent = LegendColorRole.Yellow,
    ),
    visual(0x1a, "↑", "−", accent = LegendColorRole.Yellow),
    visual(
        0x1e,
        "⇟",
        "M−",
        contentDescription = "下翻页",
        accent = LegendColorRole.Yellow,
    ),
    visual(0x38, "求助", accent = LegendColorRole.Yellow),
    visual(0x39, "中英数", above = "SHIFT"),
    visual(0x3a, "输入法", above = "CAPS"),
    visual(0x3b, "跳出", "AC"),
    visual(0x3c, "0", above = "继续"),
    visual(0x3d, ".", "●"),
    visual(0x3e, "空格", "="),
    visual(0x3f, "←", accent = LegendColorRole.Yellow),
    visual(0x1b, "↓", "+", accent = LegendColorRole.Yellow),
    visual(0x1f, "→", "M+", accent = LegendColorRole.Yellow),
).associateBy(KeyVisualSpec::keyId)

@Composable
fun FullKeyboardKeyPreview(
    keyId: Int,
    modifier: Modifier = Modifier,
) {
    val visual = fullKeyboardVisuals[keyId] ?: return
    BoxWithConstraints(modifier = modifier) {
        val narrow = maxWidth < 52.dp
        val capHeight = (maxHeight * 0.62f).coerceIn(18.dp, 31.dp)
        EmulatorKeyPreview(
            spec = visual,
            modifier = Modifier.fillMaxSize(),
            keycapHeight = capHeight,
            primaryFontSize = if (narrow) 7.sp else 9.sp,
            secondaryFontSize = if (narrow) 5.5.sp else 7.sp,
        )
    }
}

private fun visual(
    keyId: Int,
    primary: String,
    secondary: String? = null,
    above: String? = null,
    below: String? = null,
    contentDescription: String? = null,
    tone: KeyVisualTone = KeyVisualTone.Graphite,
    accent: LegendColorRole = LegendColorRole.Teal,
): KeyVisualSpec = KeyVisualSpec(
    keyId = keyId,
    primaryLabel = primary,
    secondaryLabel = secondary,
    contentDescription = contentDescription,
    legends = buildList {
        above?.let { add(KeyVisualLegend(it, LegendPosition.Above, LegendColorRole.Rose)) }
        below?.let { add(KeyVisualLegend(it, LegendPosition.Below, LegendColorRole.Rose)) }
    },
    tone = tone,
    accentRole = accent,
)

@Composable
fun FullKeyboardLayout(
    enabled: Boolean,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shortcutContent: (@Composable () -> Unit)? = null,
) {
    val panelShape = RoundedCornerShape(8.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(NcColorTokens.DeviceSilverLight, NcColorTokens.DeviceSilver),
                ),
            )
            .border(1.dp, NcColorTokens.DeviceSilverShadow, panelShape)
            .padding(horizontal = 5.dp, vertical = 6.dp),
    ) {
        val narrow = maxWidth < 400.dp
        val keyGap = if (narrow) 2.dp else 4.dp
        val handGap = if (narrow) 6.dp else 10.dp
        val rowHeight = if (narrow) 48.dp else 50.dp
        val capHeight = if (narrow) 28.dp else 31.dp
        val rowGap = if (narrow) 1.dp else 2.dp
        Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
            if (shortcutContent == null) {
                fullKeyboardRows.take(2).forEach { row ->
                    FullKeyboardRow(
                        row = row,
                        columns = FullKeyboardColumns,
                        enabled = enabled,
                        rowHeight = rowHeight,
                        capHeight = capHeight,
                        keyGap = keyGap,
                        narrow = narrow,
                        onKeyPressedChange = onKeyPressedChange,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(rowHeight * 2 + rowGap),
                    horizontalArrangement = Arrangement.spacedBy(keyGap),
                ) {
                    Box(modifier = Modifier.weight(2.35f)) { shortcutContent() }
                    Column(
                        modifier = Modifier.weight(7.65f),
                        verticalArrangement = Arrangement.spacedBy(rowGap),
                    ) {
                        fullKeyboardRows.take(2).forEach { row ->
                            val groups = if (row.size == 6) {
                                listOf(row.take(3), row.drop(3))
                            } else {
                                listOf(row)
                            }
                            FullKeyboardGroupedRow(
                                groups = groups,
                                enabled = enabled,
                                rowHeight = rowHeight,
                                capHeight = capHeight,
                                keyGap = keyGap,
                                groupGap = handGap,
                                narrow = narrow,
                                onKeyPressedChange = onKeyPressedChange,
                            )
                        }
                    }
                }
            }
            fullKeyboardTouchRows.forEach { row ->
                FullKeyboardGroupedRow(
                    groups = row.groups,
                    enabled = enabled,
                    rowHeight = rowHeight,
                    capHeight = capHeight,
                    keyGap = keyGap,
                    groupGap = handGap,
                    narrow = narrow,
                    onKeyPressedChange = onKeyPressedChange,
                )
            }
        }
    }
}

@Composable
fun MiniKeyboardLayout(
    enabled: Boolean,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
    shortcutContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(8.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(NcColorTokens.DeviceSilverLight, NcColorTokens.DeviceSilver),
                ),
            )
            .border(1.dp, NcColorTokens.DeviceSilverShadow, panelShape)
            .padding(horizontal = 5.dp, vertical = 6.dp),
    ) {
        val narrow = maxWidth < 400.dp
        val keyGap = if (narrow) 3.dp else 4.dp
        val rowGap = 2.dp
        val rowHeight = if (narrow) 44.dp else 48.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 5.dp else 7.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(if (narrow) 52.dp else 56.dp)) {
                shortcutContent()
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight * 3 + rowGap * 2),
                horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 14.dp),
            ) {
                MiniKeyCluster(
                    rows = miniDirectionRows,
                    enabled = enabled,
                    rowHeight = rowHeight,
                    rowGap = rowGap,
                    keyGap = keyGap,
                    narrow = narrow,
                    onKeyPressedChange = onKeyPressedChange,
                    modifier = Modifier.weight(1.15f),
                )
                MiniKeyCluster(
                    rows = miniActionRows,
                    enabled = enabled,
                    rowHeight = rowHeight,
                    rowGap = rowGap,
                    keyGap = if (narrow) 5.dp else 7.dp,
                    narrow = narrow,
                    onKeyPressedChange = onKeyPressedChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniKeyCluster(
    rows: List<List<Int?>>,
    enabled: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    rowGap: androidx.compose.ui.unit.Dp,
    keyGap: androidx.compose.ui.unit.Dp,
    narrow: Boolean,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(rowGap)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(keyGap),
            ) {
                row.forEach { keyId ->
                    if (keyId == null) {
                        Box(Modifier.weight(1f))
                    } else {
                        val visual = requireNotNull(fullKeyboardVisuals[keyId])
                        EmulatorKeyVisual(
                            spec = visual,
                            form = KeyVisualForm.Full,
                            enabled = enabled,
                            onPressedChange = { pressed -> onKeyPressedChange(keyId, pressed) },
                            modifier = Modifier.weight(1f),
                            keycapHeight = if (narrow) 28.dp else 31.dp,
                            primaryFontSize = if (narrow) 7.sp else 9.sp,
                            secondaryFontSize = if (narrow) 5.5.sp else 7.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullKeyboardGroupedRow(
    groups: List<List<EmulatorKeySpec>>,
    enabled: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    capHeight: androidx.compose.ui.unit.Dp,
    keyGap: androidx.compose.ui.unit.Dp,
    groupGap: androidx.compose.ui.unit.Dp,
    narrow: Boolean,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
        groups.forEachIndexed { index, group ->
            FullKeyboardRow(
                row = group,
                columns = group.size,
                enabled = enabled,
                rowHeight = rowHeight,
                capHeight = capHeight,
                keyGap = keyGap,
                narrow = narrow,
                modifier = Modifier.weight(group.size.toFloat()),
                onKeyPressedChange = onKeyPressedChange,
            )
            if (index != groups.lastIndex) Spacer(Modifier.width(groupGap))
        }
    }
}

@Composable
private fun FullKeyboardRow(
    row: List<EmulatorKeySpec>,
    columns: Int,
    enabled: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    capHeight: androidx.compose.ui.unit.Dp,
    keyGap: androidx.compose.ui.unit.Dp,
    narrow: Boolean,
    modifier: Modifier = Modifier,
    onKeyPressedChange: (keyId: Int, pressed: Boolean) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(keyGap),
    ) {
        row.forEach { key ->
            val visual = requireNotNull(fullKeyboardVisuals[key.keyId])
            EmulatorKeyVisual(
                spec = visual,
                form = KeyVisualForm.Full,
                enabled = enabled,
                onPressedChange = { pressed -> onKeyPressedChange(key.keyId, pressed) },
                modifier = Modifier.weight(1f),
                keycapHeight = capHeight,
                primaryFontSize = if (narrow) 7.sp else 9.sp,
                secondaryFontSize = if (narrow) 5.5.sp else 7.sp,
            )
        }
        repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 320)
@Composable
private fun FullKeyboard360Preview() {
    NC2000Theme {
        Surface(color = NcColorTokens.AppBackground) {
            FullKeyboardLayout(true, { _, _ -> }, Modifier.padding(8.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 600, heightDp = 370)
@Composable
private fun FullKeyboard600Preview() {
    NC2000Theme {
        Surface(color = NcColorTokens.AppBackground) {
            FullKeyboardLayout(true, { _, _ -> }, Modifier.padding(12.dp))
        }
    }
}
