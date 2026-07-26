package io.github.wangyu.nc2000.launcher

import org.json.JSONArray
import org.json.JSONObject

internal object LaunchProfileJson {
    fun encode(profiles: List<LaunchProfile>): String = JSONArray().apply {
        profiles.forEach { put(it.toJson()) }
    }.toString()

    fun decode(value: String): List<LaunchProfile> = runCatching {
        val array = JSONArray(value)
        buildList {
            repeat(array.length()) { index ->
                add(array.getJSONObject(index).toLaunchProfile())
            }
        }
    }.getOrElse { emptyList() }

    private fun LaunchProfile.toJson() = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("id", id)
        put("name", name)
        put("model", model.name)
        put("firmware", JSONObject().apply {
            putNullable("romUri", firmware.romUri)
            putNullable("norUri", firmware.norUri)
            putNullable("nandUri", firmware.nandUri)
            putNullable("nand0Uri", firmware.nand0Uri)
            putNullable("stateUri", firmware.stateUri)
        })
        put("features", JSONObject().apply {
            put("loadState", features.loadState)
            put("autoSaveFlash", features.autoSaveFlash)
            put("autoSaveState", features.autoSaveState)
            put("quickSaveFlash", features.quickSaveFlash)
            put("quickSaveState", features.quickSaveState)
            put("autoTimeSync", features.autoTimeSync)
            put("syncOnResume", features.syncOnResume)
            put("keepPowerOn", features.keepPowerOn)
            put("overclockFactor", features.overclockFactor)
            put("fastForwardLimit", features.fastForwardLimit)
        })
        put("icon", JSONObject().apply {
            put("kind", icon.kind.name)
            put("value", icon.value)
        })
        putNullable("controlSceneId", controlSceneId)
    }

    private fun JSONObject.toLaunchProfile(): LaunchProfile {
        val firmware = optJSONObject("firmware") ?: JSONObject()
        val features = optJSONObject("features") ?: JSONObject()
        val icon = optJSONObject("icon") ?: JSONObject()
        return LaunchProfile(
            schemaVersion = LaunchProfile.CURRENT_SCHEMA_VERSION,
            id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            name = optString("name", "未命名入口"),
            model = enumValueOrDefault(optString("model"), MachineModel.NC1020),
            firmware = FirmwareFiles(
                romUri = firmware.optNullableString("romUri"),
                norUri = firmware.optNullableString("norUri"),
                nandUri = firmware.optNullableString("nandUri"),
                nand0Uri = firmware.optNullableString("nand0Uri"),
                stateUri = firmware.optNullableString("stateUri"),
            ),
            features = EmulatorFeatures(
                loadState = features.optBoolean("loadState", false),
                autoSaveFlash = features.optBoolean("autoSaveFlash", false),
                autoSaveState = features.optBoolean("autoSaveState", false),
                quickSaveFlash = features.optBoolean("quickSaveFlash", false),
                quickSaveState = features.optBoolean("quickSaveState", true),
                autoTimeSync = features.optBoolean("autoTimeSync", true),
                syncOnResume = features.optBoolean("syncOnResume", true),
                keepPowerOn = features.optBoolean("keepPowerOn", false),
                overclockFactor = features.optDouble("overclockFactor", 1.0),
                fastForwardLimit = features.optInt("fastForwardLimit", 5),
            ),
            icon = ProfileIcon(
                kind = enumValueOrDefault(icon.optString("kind"), ProfileIconKind.BUILT_IN),
                value = icon.optString("value", "calculator"),
            ),
            controlSceneId = optNullableString("controlSceneId"),
        )
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).ifBlank { null }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
