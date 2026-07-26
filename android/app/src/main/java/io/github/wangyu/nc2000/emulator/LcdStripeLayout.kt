package io.github.wangyu.nc2000.emulator

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject

internal data class LcdRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal data class LcdStripe(
    val source: LcdRect,
    val left: Int,
    val top: Int,
)

internal data class LcdStripeLayout(
    val width: Int,
    val height: Int,
    val background: LcdRect,
    val mainLeft: Int,
    val mainTop: Int,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val stripes: List<LcdStripe>,
)

internal data class LcdStripeAssets(
    val texture: ImageBitmap,
    val layout: LcdStripeLayout,
) {
    companion object {
        fun load(context: Context, suffix: String = "w938"): LcdStripeAssets {
            val texture = context.assets.open("lcdstripe_$suffix.bmp").use { stream ->
                requireNotNull(BitmapFactory.decodeStream(stream)) {
                    "无法读取 LCD 段码纹理：$suffix"
                }.asImageBitmap()
            }
            val json = context.assets.open("lcdstripe_slice_$suffix.json")
                .bufferedReader()
                .use { JSONObject(it.readText()) }
            return LcdStripeAssets(texture, json.toLayout())
        }
    }
}

private fun JSONObject.toLayout(): LcdStripeLayout {
    val misc = getJSONObject("misc")
    val gap = misc.getJSONObject("gap")
    val lcd = misc.getJSONObject("lcd")
    val stripes = arrayOfNulls<LcdStripe>(80)

    fun stripe(key: String): LcdStripe {
        val item = getJSONObject(key)
        val slice = item.getJSONObject("slice")
        val frame = item.getJSONObject("frame")
        return LcdStripe(
            source = LcdRect(
                x = frame.getInt("x"),
                y = frame.getInt("y"),
                width = frame.getInt("w"),
                height = frame.getInt("h"),
            ),
            left = slice.getInt("x"),
            top = slice.getInt("y"),
        )
    }

    fun copyMoved(destination: Int, source: Int, step: Int) {
        val original = requireNotNull(stripes[source])
        stripes[destination] = original.copy(left = original.left + step)
    }

    val line = stripe("lcd_line")
    val evenLine = stripe("lcd_line5")
    val verticalBar = stripe("lcd_vertbar")
    val horizontalBar = stripe("lcd_hbar")
    val pixel = stripe("lcdpixel")

    stripes[0] = stripe("lcd_seven_vert3")
    stripes[1] = stripe("lcd_seven_honz1")
    stripes[2] = stripe("lcd_seven_vert1")
    stripes[3] = stripe("lcd_seven_honz2")
    stripes[33] = stripe("lcd_seven_vert2")
    stripes[34] = stripe("lcd_seven_honz3")
    stripes[35] = stripe("lcd_seven_vert4")

    val sevenStep = gap.getInt("7seg")
    copyMoved(5, 0, sevenStep)
    copyMoved(6, 1, sevenStep)
    copyMoved(7, 2, sevenStep)
    copyMoved(8, 3, sevenStep)
    copyMoved(29, 33, sevenStep)
    copyMoved(30, 34, sevenStep)
    copyMoved(31, 35, sevenStep)

    copyMoved(10, 5, sevenStep)
    copyMoved(11, 6, sevenStep)
    copyMoved(13, 7, sevenStep)
    copyMoved(14, 8, sevenStep)
    copyMoved(24, 29, sevenStep)
    copyMoved(25, 30, sevenStep)
    copyMoved(26, 31, sevenStep)

    copyMoved(15, 10, sevenStep)
    copyMoved(16, 11, sevenStep)
    copyMoved(17, 13, sevenStep)
    copyMoved(18, 14, sevenStep)
    copyMoved(19, 24, sevenStep)
    copyMoved(21, 25, sevenStep)
    copyMoved(22, 26, sevenStep)

    stripes[32] = stripe("lcd_point")
    stripes[9] = stripe("lcd_semicolon")
    copyMoved(27, 32, sevenStep)
    copyMoved(23, 27, sevenStep)

    val lineGap = gap.getInt("line")
    val alternatingLines = gap.optBoolean("oddline")
    val lineIndices = intArrayOf(4, 12, 20, 28, 36, 44, 52, 60, 68)
    lineIndices.forEachIndexed { index, destination ->
        val source = if (alternatingLines && index % 2 == 1) evenLine else line
        val step = if (alternatingLines) index / 2 else index
        stripes[destination] = source.copy(top = source.top + step * lineGap)
    }
    stripes[70] = stripe("lcd_right")
    val lastLine = if (alternatingLines) evenLine else line
    val lastLineStep = if (alternatingLines) lineIndices.size / 2 else lineIndices.size
    stripes[74] = lastLine.copy(top = lastLine.top + lastLineStep * lineGap)

    mapOf(
        38 to "lcd_pgup",
        37 to "lcd_star",
        39 to "lcd_num",
        40 to "lcd_eng",
        41 to "lcd_caps",
        42 to "lcd_shift",
        46 to "lcd_flash",
        47 to "lcd_sound",
        48 to "lcd_keyclick",
        51 to "lcd_sharpbell",
        50 to "lcd_speaker",
        49 to "lcd_alarm",
        53 to "lcd_microphone",
        54 to "lcd_tape",
        55 to "lcd_minus",
        58 to "lcd_battery",
        59 to "lcd_secret",
        61 to "lcd_pgleft",
        62 to "lcd_pgright",
        63 to "lcd_left",
        64 to "lcd_pgdown",
        65 to "lcd_vframe",
        79 to "lcd_up",
        66 to "lcd_down",
        72 to "lcd_hframe",
    ).forEach { (index, key) -> stripes[index] = stripe(key) }

    val verticalStep = verticalBar.source.height
    listOf(43, 45, 56, 78, 77, 57, 76, 75, 73).forEachIndexed { index, destination ->
        stripes[destination] = verticalBar.copy(top = verticalBar.top + index * verticalStep)
    }
    val horizontalStep = horizontalBar.source.width
    stripes[67] = horizontalBar
    stripes[69] = horizontalBar.copy(left = horizontalBar.left + horizontalStep)
    stripes[71] = horizontalBar.copy(left = horizontalBar.left + horizontalStep * 2)

    val background = stripe("lcdoverlap").source
    return LcdStripeLayout(
        width = lcd.getInt("w"),
        height = lcd.getInt("h"),
        background = background,
        mainLeft = 21 * pixel.source.width,
        mainTop = 0,
        pixelWidth = pixel.source.width,
        pixelHeight = pixel.source.height,
        stripes = stripes.map { requireNotNull(it) },
    )
}
