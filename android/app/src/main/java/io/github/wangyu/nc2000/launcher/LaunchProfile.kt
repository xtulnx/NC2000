package io.github.wangyu.nc2000.launcher

import java.util.UUID

enum class MachineModel(
    val displayName: String,
    val requiresMaskRom: Boolean,
    val requiresNand: Boolean,
) {
    NC1020("NC1020", requiresMaskRom = true, requiresNand = false),
    NC1020_TW("NC1020 台湾版", requiresMaskRom = true, requiresNand = false),
    NC2000("NC2000 / NC2600", requiresMaskRom = false, requiresNand = true),
    NC3000("NC3000（实验）", requiresMaskRom = false, requiresNand = true),
    PC1000("PC1000（实验）", requiresMaskRom = true, requiresNand = false),
}

data class FirmwareFiles(
    val romUri: String? = null,
    val norUri: String? = null,
    val nandUri: String? = null,
    val nand0Uri: String? = null,
    val stateUri: String? = null,
)

data class EmulatorFeatures(
    val loadState: Boolean = false,
    val autoSaveFlash: Boolean = false,
    val autoSaveState: Boolean = false,
    val quickSaveFlash: Boolean = false,
    val quickSaveState: Boolean = true,
    val autoTimeSync: Boolean = true,
    val syncOnResume: Boolean = true,
    val keepPowerOn: Boolean = false,
    val overclockFactor: Double = 1.0,
    val fastForwardLimit: Int = 5,
)

enum class ProfileIconKind {
    BUILT_IN,
    CUSTOM_URI,
}

data class ProfileIcon(
    val kind: ProfileIconKind = ProfileIconKind.BUILT_IN,
    val value: String = "calculator",
)

data class LaunchProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val model: MachineModel,
    val firmware: FirmwareFiles = FirmwareFiles(),
    val features: EmulatorFeatures = EmulatorFeatures(),
    val icon: ProfileIcon = ProfileIcon(),
    val controlSceneId: String? = null,
) {
    fun validationErrors(): List<String> = buildList {
        if (name.isBlank()) add("请输入入口名称")
        if (firmware.norUri.isNullOrBlank()) add("请选择 NOR 文件")
        if (model.requiresMaskRom && firmware.romUri.isNullOrBlank()) {
            add("${model.displayName} 需要 ROM 文件")
        }
        if (model.requiresNand && firmware.nandUri.isNullOrBlank()) {
            add("${model.displayName} 需要 NAND 文件")
        }
        if (model.requiresNand && firmware.nand0Uri.isNullOrBlank()) {
            add("${model.displayName} 需要 NAND0 文件")
        }
        // A STATE file may have been created by an earlier in-app save even
        // when no external STATE document is attached. Native validation gives
        // the precise error if neither source exists at launch time.
        if (features.overclockFactor !in 0.1..20.0) {
            add("主频倍率必须在 0.1 到 20.0 之间")
        }
        if (features.fastForwardLimit < 0) {
            add("快进上限不能为负数")
        }
        if (!features.quickSaveFlash && !features.quickSaveState) {
            add("快捷存档至少需要包含 NOR 或 STATE")
        }
        if (controlSceneId != null && controlSceneId.isBlank()) {
            add("控制场景编号不能为空")
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3

        fun defaultNc1020() = LaunchProfile(
            name = "NC1020 官方 3.6",
            model = MachineModel.NC1020,
        )
    }
}

data class SelectedDocument(
    val displayName: String,
    val uri: String,
)
