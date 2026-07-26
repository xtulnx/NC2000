package io.github.wangyu.nc2000.controls

import java.util.UUID

enum class VirtualControlShape {
    RECTANGLE,
    CIRCLE,
}

enum class LcdFrameStyle {
    SIMPLE,
    CLASSIC_BEZEL,
}

enum class VirtualControlActionKind {
    KEY,
    KEY_COMBINATION,
    HOLD_FAST_FORWARD,
    TOGGLE_FAST_FORWARD,
    OPEN_RUNTIME_MENU,
}

data class VirtualControlAction(
    val kind: VirtualControlActionKind,
    val keyIds: List<Int> = emptyList(),
)

data class VirtualControl(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val shape: VirtualControlShape = VirtualControlShape.RECTANGLE,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val opacity: Float = 0.55f,
    val action: VirtualControlAction,
) {
    fun validationErrors(): List<String> = buildList {
        if (label.isBlank()) add("虚拟按键标签不能为空")
        if (x !in 0f..1f || y !in 0f..1f) add("虚拟按键位置必须在画布范围内")
        if (width <= 0f || height <= 0f || x + width > 1f || y + height > 1f) {
            add("虚拟按键尺寸超出画布范围")
        }
        if (opacity !in 0f..1f) add("虚拟按键透明度必须在 0 到 1 之间")
        if (action.kind == VirtualControlActionKind.KEY && action.keyIds.size != 1) {
            add("单键动作必须映射一个按键")
        }
        if (action.kind == VirtualControlActionKind.KEY_COMBINATION && action.keyIds.size < 2) {
            add("组合键动作至少需要两个按键")
        }
        if (action.keyIds.any { it !in 0x00..0x3f }) add("按键编号必须在 0x00 到 0x3f 之间")
    }
}

data class ControlSceneLayout(
    val controls: List<VirtualControl> = emptyList(),
)

data class ControlScene(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lcdFrameStyle: LcdFrameStyle = LcdFrameStyle.SIMPLE,
    val portrait: ControlSceneLayout = ControlSceneLayout(),
    val landscape: ControlSceneLayout = ControlSceneLayout(),
) {
    fun validationErrors(): List<String> = buildList {
        if (name.isBlank()) add("场景名称不能为空")
        portrait.controls.forEach { addAll(it.validationErrors()) }
        landscape.controls.forEach { addAll(it.validationErrors()) }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val DEFAULT_GAME_SCENE_ID = "builtin-game-overlay"

        fun defaultGameOverlay() = ControlScene(
            id = DEFAULT_GAME_SCENE_ID,
            name = "游戏模式",
            lcdFrameStyle = LcdFrameStyle.CLASSIC_BEZEL,
            portrait = ControlSceneLayout(
                controls = listOf(
                    key("上", 0x1a, VirtualControlShape.CIRCLE, 0.12f, 0.56f, 0.14f, 0.09f),
                    key("左", 0x3f, VirtualControlShape.CIRCLE, 0.03f, 0.66f, 0.14f, 0.09f),
                    key("右", 0x1f, VirtualControlShape.CIRCLE, 0.21f, 0.66f, 0.14f, 0.09f),
                    key("下", 0x1b, VirtualControlShape.CIRCLE, 0.12f, 0.76f, 0.14f, 0.09f),
                    key("输入", 0x1d, VirtualControlShape.CIRCLE, 0.73f, 0.61f, 0.16f, 0.10f),
                    key("跳出", 0x3b, VirtualControlShape.CIRCLE, 0.73f, 0.75f, 0.16f, 0.10f),
                    action("加速", VirtualControlActionKind.HOLD_FAST_FORWARD, 0.40f, 0.86f, 0.20f, 0.08f),
                ),
            ),
            landscape = ControlSceneLayout(
                controls = listOf(
                    key("上", 0x1a, VirtualControlShape.RECTANGLE, 0.09f, 0.31f, 0.08f, 0.12f),
                    key("左", 0x3f, VirtualControlShape.RECTANGLE, 0.03f, 0.43f, 0.08f, 0.12f),
                    key("右", 0x1f, VirtualControlShape.RECTANGLE, 0.15f, 0.43f, 0.08f, 0.12f),
                    key("下", 0x1b, VirtualControlShape.RECTANGLE, 0.09f, 0.55f, 0.08f, 0.12f),
                    key("Y", 0x25, VirtualControlShape.CIRCLE, 0.88f, 0.31f, 0.08f, 0.12f),
                    key("输入", 0x1d, VirtualControlShape.CIRCLE, 0.82f, 0.43f, 0.08f, 0.12f),
                    key("跳出", 0x3b, VirtualControlShape.CIRCLE, 0.94f, 0.43f, 0.06f, 0.12f),
                    key("N", 0x35, VirtualControlShape.CIRCLE, 0.88f, 0.55f, 0.08f, 0.12f),
                    action("加速", VirtualControlActionKind.HOLD_FAST_FORWARD, 0.43f, 0.89f, 0.14f, 0.08f),
                    action("菜单", VirtualControlActionKind.OPEN_RUNTIME_MENU, 0.88f, 0.05f, 0.10f, 0.07f),
                ),
            ),
        )

        private fun key(
            label: String,
            keyId: Int,
            shape: VirtualControlShape,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
        ) = VirtualControl(
            label = label,
            shape = shape,
            x = x,
            y = y,
            width = width,
            height = height,
            action = VirtualControlAction(VirtualControlActionKind.KEY, listOf(keyId)),
        )

        private fun action(
            label: String,
            kind: VirtualControlActionKind,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
        ) = VirtualControl(
            label = label,
            x = x,
            y = y,
            width = width,
            height = height,
            action = VirtualControlAction(kind),
        )
    }
}
