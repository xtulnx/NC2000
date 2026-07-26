package io.github.wangyu.nc2000.controls

import org.json.JSONArray
import org.json.JSONObject

internal object ControlSceneJson {
    fun encode(scenes: List<ControlScene>): String = JSONArray().apply {
        scenes.forEach { put(it.toJson()) }
    }.toString()

    fun decode(value: String): List<ControlScene> = runCatching {
        val array = JSONArray(value)
        buildList {
            repeat(array.length()) { index -> add(array.getJSONObject(index).toScene()) }
        }
    }.getOrElse { emptyList() }

    private fun ControlScene.toJson() = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("id", id)
        put("name", name)
        put("lcdFrameStyle", lcdFrameStyle.name)
        put("portrait", portrait.toJson())
        put("landscape", landscape.toJson())
    }

    private fun ControlSceneLayout.toJson() = JSONObject().apply {
        put("controls", JSONArray().apply { controls.forEach { put(it.toJson()) } })
    }

    private fun VirtualControl.toJson() = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("shape", shape.name)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("width", width.toDouble())
        put("height", height.toDouble())
        put("opacity", opacity.toDouble())
        put("action", JSONObject().apply {
            put("kind", action.kind.name)
            put("keyIds", JSONArray().apply { action.keyIds.forEach(::put) })
        })
    }

    private fun JSONObject.toScene() = ControlScene(
        schemaVersion = ControlScene.CURRENT_SCHEMA_VERSION,
        id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        name = optString("name", "未命名场景"),
        lcdFrameStyle = enumValueOrDefault(
            optString("lcdFrameStyle"),
            LcdFrameStyle.SIMPLE,
        ),
        portrait = optJSONObject("portrait").toLayout(),
        landscape = optJSONObject("landscape").toLayout(),
    )

    private fun JSONObject?.toLayout(): ControlSceneLayout {
        val array = this?.optJSONArray("controls") ?: JSONArray()
        return ControlSceneLayout(
            controls = buildList {
                repeat(array.length()) { index -> add(array.getJSONObject(index).toControl()) }
            },
        )
    }

    private fun JSONObject.toControl(): VirtualControl {
        val action = optJSONObject("action") ?: JSONObject()
        val keyIds = action.optJSONArray("keyIds") ?: JSONArray()
        return VirtualControl(
            id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            label = optString("label", "按键"),
            shape = enumValueOrDefault(optString("shape"), VirtualControlShape.RECTANGLE),
            x = optDouble("x", 0.1).toFloat(),
            y = optDouble("y", 0.1).toFloat(),
            width = optDouble("width", 0.1).toFloat(),
            height = optDouble("height", 0.1).toFloat(),
            opacity = optDouble("opacity", 0.55).toFloat(),
            action = VirtualControlAction(
                kind = enumValueOrDefault(
                    action.optString("kind"),
                    VirtualControlActionKind.KEY,
                ),
                keyIds = buildList {
                    repeat(keyIds.length()) { index -> add(keyIds.optInt(index)) }
                },
            ),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
