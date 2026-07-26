package io.github.wangyu.nc2000.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = NcColorTokens.KeyTealDark,
    onPrimary = NcColorTokens.SurfaceWarm,
    primaryContainer = Color(0xffc6eeeb),
    onPrimaryContainer = Color(0xff123c3d),
    secondary = NcColorTokens.DeviceNavy,
    onSecondary = NcColorTokens.SurfaceWarm,
    secondaryContainer = Color(0xffdce2ea),
    onSecondaryContainer = NcColorTokens.DeviceNavy,
    tertiary = NcColorTokens.AccentOrange,
    background = NcColorTokens.AppBackground,
    onBackground = NcColorTokens.TextPrimary,
    surface = NcColorTokens.SurfaceWarm,
    onSurface = NcColorTokens.TextPrimary,
    surfaceVariant = NcColorTokens.DeviceSilver,
    onSurfaceVariant = NcColorTokens.TextSecondary,
    outline = NcColorTokens.DeviceSilverShadow,
)

private val DarkColors = darkColorScheme(
    primary = NcColorTokens.KeyTeal,
    onPrimary = Color(0xff003737),
    primaryContainer = Color(0xff175457),
    secondary = Color(0xffb9c6d8),
    tertiary = Color(0xffffb68f),
    background = Color(0xff181b1b),
    surface = Color(0xff202424),
    onSurface = Color(0xffe4e4df),
    surfaceVariant = Color(0xff414746),
    onSurfaceVariant = Color(0xffc4c8c5),
    outline = Color(0xff8e938f),
)

@Composable
fun NC2000Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
