package io.github.wangyu.nc2000.emulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wangyu.nc2000.ui.theme.NcColorTokens
import kotlin.math.roundToInt

@Composable
fun EmulatorShortcutPad(
    enabled: Boolean,
    fastForwardLocked: Boolean,
    fastForwardActive: Boolean,
    fastForwardMultiplier: Double?,
    quickSaveTarget: String,
    onFastForwardPressedChange: (Boolean) -> Unit,
    onFastForwardLockedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ShortcutButton(
                symbol = "≫",
                label = fastForwardMultiplier?.let(::fastForwardMultiplierText)
                    ?: if (fastForwardActive) "加速中" else "加速",
                enabled = enabled,
                selected = fastForwardActive,
                fastForwardLocked = fastForwardLocked,
                onFastForwardPressedChange = onFastForwardPressedChange,
                onFastForwardLockedChange = onFastForwardLockedChange,
                onSemanticClick = { onFastForwardLockedChange(!fastForwardLocked) },
                modifier = Modifier.weight(1f),
            )
            ShortcutButton(
                symbol = "▣",
                label = "保存",
                supportingText = quickSaveTarget,
                enabled = enabled,
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ShortcutButton(
                symbol = "□",
                label = "读档",
                supportingText = quickSaveTarget,
                enabled = enabled,
                onClick = onLoad,
                modifier = Modifier.weight(1f),
            )
            ShortcutButton(
                symbol = "•••",
                label = "菜单",
                enabled = enabled,
                onClick = onOpenMenu,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun EmulatorShortcutStrip(
    enabled: Boolean,
    fastForwardLocked: Boolean,
    fastForwardActive: Boolean,
    fastForwardMultiplier: Double?,
    quickSaveTarget: String,
    onFastForwardPressedChange: (Boolean) -> Unit,
    onFastForwardLockedChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ShortcutButton(
            symbol = "≫",
            label = fastForwardMultiplier?.let(::fastForwardMultiplierText)
                ?: if (fastForwardActive) "加速中" else "加速",
            enabled = enabled,
            selected = fastForwardActive,
            fastForwardLocked = fastForwardLocked,
            onFastForwardPressedChange = onFastForwardPressedChange,
            onFastForwardLockedChange = onFastForwardLockedChange,
            onSemanticClick = { onFastForwardLockedChange(!fastForwardLocked) },
            modifier = Modifier.weight(1f),
        )
        ShortcutButton(
            symbol = "▣",
            label = "存档",
            supportingText = quickSaveTarget,
            enabled = enabled,
            onClick = onSave,
            modifier = Modifier.weight(1f),
        )
        ShortcutButton(
            symbol = "□",
            label = "读档",
            supportingText = quickSaveTarget,
            enabled = enabled,
            onClick = onLoad,
            modifier = Modifier.weight(1f),
        )
        ShortcutButton(
            symbol = "•••",
            label = "菜单",
            enabled = enabled,
            onClick = onOpenMenu,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShortcutButton(
    symbol: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    selected: Boolean = false,
    fastForwardLocked: Boolean? = null,
    onFastForwardPressedChange: ((Boolean) -> Unit)? = null,
    onFastForwardLockedChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit = {},
    onSemanticClick: () -> Unit = onClick,
) {
    val currentFastForwardLocked by rememberUpdatedState(fastForwardLocked)
    val currentFastForwardPressChange by rememberUpdatedState(onFastForwardPressedChange)
    val currentFastForwardLockedChange by rememberUpdatedState(onFastForwardLockedChange)
    val shape = RoundedCornerShape(6.dp)
    val background = when {
        selected -> Color(0xffc6eeeb)
        else -> NcColorTokens.SurfaceWarm
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        enabled -> NcColorTokens.TextPrimary
        else -> NcColorTokens.TextSecondary.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {
                contentDescription = if (supportingText == null) label else "$label，$supportingText"
                if (!enabled) disabled() else onClick {
                    onSemanticClick()
                    true
                }
            }
            .background(background, shape)
            .border(
                1.dp,
                if (selected) NcColorTokens.KeyTealDark else NcColorTokens.DeviceSilverShadow,
                shape,
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                if (
                    currentFastForwardLocked != null &&
                    currentFastForwardPressChange != null &&
                    currentFastForwardLockedChange != null
                ) {
                    var lockedBeforeGesture = false
                    var longPressTriggered = false
                    detectTapGestures(
                        onPress = {
                            lockedBeforeGesture = currentFastForwardLocked ?: false
                            longPressTriggered = false
                            try {
                                tryAwaitRelease()
                            } finally {
                                if (longPressTriggered) {
                                    currentFastForwardPressChange?.invoke(lockedBeforeGesture)
                                }
                            }
                        },
                        onLongPress = {
                            longPressTriggered = true
                            currentFastForwardPressChange?.invoke(!lockedBeforeGesture)
                        },
                        onTap = {
                            currentFastForwardLockedChange?.invoke(!lockedBeforeGesture)
                        },
                    )
                } else {
                    detectTapGestures(onTap = { onClick() })
                }
            }
            .padding(horizontal = 2.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = symbol,
                color = foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 12.sp,
            )
            Text(
                text = label,
                color = foreground,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            supportingText?.let {
                Text(
                    text = it,
                    color = foreground,
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun fastForwardMultiplierText(multiplier: Double): String {
    val rounded = (multiplier * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "×${rounded.toInt()}" else "×$rounded"
}
