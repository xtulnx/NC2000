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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wangyu.nc2000.ui.theme.NC2000Theme
import io.github.wangyu.nc2000.ui.theme.NcColorTokens

enum class KeyVisualForm {
    Compact,
    Full,
}

enum class LegendPosition {
    Above,
    Below,
}

enum class LegendColorRole {
    Muted,
    Teal,
    Rose,
    Yellow,
}

enum class KeyVisualTone {
    Graphite,
    Teal,
    Rose,
    Lime,
}

data class KeyVisualLegend(
    val text: String,
    val position: LegendPosition,
    val colorRole: LegendColorRole = LegendColorRole.Rose,
)

data class KeyVisualSpec(
    val keyId: Int,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val contentDescription: String? = null,
    val legends: List<KeyVisualLegend> = emptyList(),
    val tone: KeyVisualTone = KeyVisualTone.Graphite,
    val accentRole: LegendColorRole = LegendColorRole.Teal,
)

@Composable
fun EmulatorKeyVisual(
    spec: KeyVisualSpec,
    form: KeyVisualForm,
    enabled: Boolean,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    keycapHeight: Dp = 30.dp,
    primaryFontSize: TextUnit = 9.sp,
    secondaryFontSize: TextUnit = 7.sp,
) {
    var pressed by remember(spec.keyId) { mutableStateOf(false) }
    val accessibilityLabel = spec.contentDescription ?: buildString {
        append(spec.primaryLabel)
        spec.secondaryLabel?.let { append(' ').append(it) }
    }

    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                stateDescription = if (pressed) "已按下" else "未按下"
                if (!enabled) {
                    disabled()
                } else {
                    onClick {
                        onPressedChange(true)
                        onPressedChange(false)
                        true
                    }
                }
            }
            .pointerInput(spec.keyId, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPressedChange(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                            onPressedChange(false)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (form == KeyVisualForm.Full) {
            FullKeyVisual(
                spec,
                pressed,
                enabled,
                keycapHeight,
                primaryFontSize,
                secondaryFontSize,
            )
        } else {
            Keycap(
                spec,
                pressed,
                enabled,
                primaryFontSize,
                secondaryFontSize,
                Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Read-only rendering of a key from the full-keyboard visual library. Custom
 * layouts use this inside their independently sized touch response region.
 */
@Composable
fun EmulatorKeyPreview(
    spec: KeyVisualSpec,
    modifier: Modifier = Modifier,
    keycapHeight: Dp = 30.dp,
    primaryFontSize: TextUnit = 9.sp,
    secondaryFontSize: TextUnit = 7.sp,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        FullKeyVisual(
            spec = spec,
            pressed = false,
            enabled = true,
            keycapHeight = keycapHeight,
            primaryFontSize = primaryFontSize,
            secondaryFontSize = secondaryFontSize,
        )
    }
}

@Composable
private fun FullKeyVisual(
    spec: KeyVisualSpec,
    pressed: Boolean,
    enabled: Boolean,
    keycapHeight: Dp,
    primaryFontSize: TextUnit,
    secondaryFontSize: TextUnit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Legend(spec, LegendPosition.Above)
        Keycap(
            spec = spec,
            pressed = pressed,
            enabled = enabled,
            primaryFontSize = primaryFontSize,
            secondaryFontSize = secondaryFontSize,
            modifier = Modifier.fillMaxWidth().height(keycapHeight),
        )
        Legend(spec, LegendPosition.Below)
    }
}

@Composable
private fun Legend(spec: KeyVisualSpec, position: LegendPosition) {
    val legend = spec.legends.firstOrNull { it.position == position }
    Text(
        text = legend?.text.orEmpty(),
        modifier = Modifier.alpha(if (legend == null) 0f else 1f),
        color = legendColor(legend?.colorRole ?: LegendColorRole.Muted),
        fontSize = 7.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 8.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Keycap(
    spec: KeyVisualSpec,
    pressed: Boolean,
    enabled: Boolean,
    primaryFontSize: TextUnit,
    secondaryFontSize: TextUnit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(5.dp)
    val (topColor, bottomColor) = keycapColors(spec.tone, pressed)
    val primaryColor = when (spec.accentRole) {
        LegendColorRole.Yellow -> NcColorTokens.KeyYellow
        else -> Color(0xfff1f4f2)
    }
    Box(
        modifier = modifier
            .offset(y = if (pressed) 1.5.dp else 0.dp)
            .shadow(if (pressed) 0.dp else 1.5.dp, shape, clip = false)
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)), shape)
            .border(0.7.dp, NcColorTokens.GraphiteBorder.copy(alpha = 0.9f), shape)
            .alpha(if (enabled) 1f else 0.46f)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                text = spec.primaryLabel,
                color = primaryColor,
                fontSize = primaryFontSize,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            spec.secondaryLabel?.let { secondary ->
                Text(
                    text = secondary,
                    color = legendColor(spec.accentRole),
                    fontSize = secondaryFontSize,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun keycapColors(tone: KeyVisualTone, pressed: Boolean): Pair<Color, Color> {
    val colors = when (tone) {
        KeyVisualTone.Graphite -> NcColorTokens.GraphiteTop to NcColorTokens.Graphite
        KeyVisualTone.Teal -> Color(0xff239da1) to NcColorTokens.KeyTealDark
        KeyVisualTone.Rose -> NcColorTokens.KeyRose to NcColorTokens.KeyRoseDark
        KeyVisualTone.Lime -> Color(0xff99c641) to Color(0xff69922b)
    }
    return if (pressed) {
        colors.first.copy(alpha = 0.82f) to colors.second.copy(alpha = 0.9f)
    } else {
        colors
    }
}

@Composable
private fun legendColor(role: LegendColorRole): Color = when (role) {
    LegendColorRole.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
    LegendColorRole.Teal -> NcColorTokens.KeyTeal
    LegendColorRole.Rose -> NcColorTokens.KeyRoseDark
    LegendColorRole.Yellow -> NcColorTokens.KeyYellow
}

@Preview(showBackground = true, widthDp = 240, heightDp = 100)
@Composable
private fun KeyVisualFormsPreview() {
    NC2000Theme {
        Surface(color = NcColorTokens.AppBackground) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val spec = KeyVisualSpec(
                    keyId = 0x39,
                    primaryLabel = "中英数",
                    legends = listOf(KeyVisualLegend("SHIFT", LegendPosition.Above)),
                )
                EmulatorKeyVisual(
                    spec = spec,
                    form = KeyVisualForm.Compact,
                    enabled = true,
                    onPressedChange = {},
                    modifier = Modifier.width(92.dp).size(70.dp),
                )
                EmulatorKeyVisual(
                    spec = spec,
                    form = KeyVisualForm.Full,
                    enabled = true,
                    onPressedChange = {},
                    modifier = Modifier.width(92.dp).size(70.dp),
                )
            }
        }
    }
}
