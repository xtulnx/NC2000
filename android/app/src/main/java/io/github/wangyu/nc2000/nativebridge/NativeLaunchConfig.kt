package io.github.wangyu.nc2000.nativebridge

data class NativeLaunchConfig(
    val model: String,
    val romPath: String?,
    val norPath: String,
    val nandPath: String?,
    val nand0Path: String?,
    val statePath: String?,
    val loadState: Boolean,
    val autoSaveFlash: Boolean,
    val autoSaveState: Boolean,
    val autoTimeSync: Boolean,
    val syncOnResume: Boolean,
    val keepPowerOn: Boolean,
    val overclockFactor: Double,
    val fastForwardLimit: Int,
)
