package io.github.wangyu.nc2000.nativebridge

import android.content.Context
import io.github.wangyu.nc2000.emulator.IEmulatorSession
import org.libsdl.app.SDLAudioManager

object NativeBridge {
    private data class RemoteBinding(
        val session: IEmulatorSession,
        val profileId: String,
    )

    init {
        System.loadLibrary("nc2000_jni")
    }

    @Volatile
    private var remoteBinding: RemoteBinding? = null

    fun attach(session: IEmulatorSession, profileId: String = "") {
        remoteBinding = RemoteBinding(session, profileId)
    }

    fun buildInfo(): String {
        val remote = remoteBinding
        return if (remote != null) remote.session.buildInfo() else nativeBuildInfo()
    }

    @Synchronized
    fun initializeSdlAudio(context: Context) {
        if (!sdlAudioInitialized) {
            sdlAudioInitialized = runCatching {
                SDLAudioManager.nativeSetupJNI()
                SDLAudioManager.initialize()
            }.isSuccess
        }
        if (sdlAudioInitialized) SDLAudioManager.setContext(context.applicationContext)
        nativeSetAudioAvailable(sdlAudioInitialized)
    }

    fun configure(config: NativeLaunchConfig): String? {
        val remote = remoteBinding
        return if (remote != null) {
            remote.session.configure(
                remote.profileId,
                config.model,
                config.romPath,
                config.norPath,
                config.nandPath,
                config.nand0Path,
                config.statePath,
                config.loadState,
                config.autoSaveFlash,
                config.autoSaveState,
                config.autoTimeSync,
                config.syncOnResume,
                config.keepPowerOn,
                config.overclockFactor,
                config.fastForwardLimit,
            )
        } else {
            nativeConfigure(
                model = config.model,
                romPath = config.romPath,
                norPath = config.norPath,
                nandPath = config.nandPath,
                nand0Path = config.nand0Path,
                statePath = config.statePath,
                loadState = config.loadState,
                autoSaveFlash = config.autoSaveFlash,
                autoSaveState = config.autoSaveState,
                autoTimeSync = config.autoTimeSync,
                syncOnResume = config.syncOnResume,
                keepPowerOn = config.keepPowerOn,
                overclockFactor = config.overclockFactor,
                fastForwardLimit = config.fastForwardLimit,
            )
        }
    }

    fun start(): String? {
        val remote = remoteBinding
        return if (remote != null) remote.session.start() else nativeStart()
    }

    fun pause() {
        val remote = remoteBinding
        if (remote != null) remote.session.pause() else nativePause()
    }

    fun resume() {
        val remote = remoteBinding
        if (remote != null) remote.session.resume() else nativeResume()
    }

    fun continueInBackground() {
        val remote = remoteBinding
        if (remote != null) remote.session.continueInBackground()
        else nativeContinueInBackground()
    }

    fun stop() {
        val remote = remoteBinding
        if (remote != null) remote.session.stop() else nativeStop()
    }

    fun isActive(): Boolean = remoteBinding?.session?.isActive ?: nativeIsActive()

    fun copyLcdFrame(destination: ByteArray, lastSequence: Long): Long {
        val remote = remoteBinding
        return if (remote != null) remote.session.copyLcdFrame(destination, lastSequence)
        else nativeCopyLcdFrame(destination, lastSequence)
    }

    fun setKey(keyId: Int, pressed: Boolean) {
        val remote = remoteBinding
        if (remote != null) remote.session.setKey(keyId, pressed) else nativeSetKey(keyId, pressed)
    }

    fun setFastForward(enabled: Boolean) {
        val remote = remoteBinding
        if (remote != null) remote.session.setFastForward(enabled) else nativeSetFastForward(enabled)
    }

    fun fastForwardMultiplier(): Double {
        val remote = remoteBinding
        return if (remote != null) remote.session.fastForwardMultiplier()
        else nativeFastForwardMultiplier()
    }

    fun requestReset() {
        val remote = remoteBinding
        if (remote != null) remote.session.requestReset() else nativeRequestReset()
    }

    fun requestSave(includeFlash: Boolean, includeState: Boolean) {
        val remote = remoteBinding
        if (remote != null) remote.session.requestSave(includeFlash, includeState)
        else nativeRequestSave(includeFlash, includeState)
    }

    fun requestLoad(includeFlash: Boolean, includeState: Boolean): Boolean {
        val remote = remoteBinding
        return if (remote != null) remote.session.requestLoad(includeFlash, includeState)
        else nativeRequestLoad(includeFlash, includeState)
    }

    /** Starts a narrow, file-only import; the source is never marshalled through Binder. */
    fun startImport(sourcePath: String, deviceNameGbk: ByteArray): String? {
        val remote = remoteBinding
        return if (remote != null) remote.session.startImport(sourcePath, deviceNameGbk)
        else nativeStartImport(sourcePath, deviceNameGbk)
    }

    fun importStatus(): String = remoteBinding?.session?.importStatus() ?: nativeImportStatus()

    private external fun nativeConfigure(
        model: String,
        romPath: String?,
        norPath: String,
        nandPath: String?,
        nand0Path: String?,
        statePath: String?,
        loadState: Boolean,
        autoSaveFlash: Boolean,
        autoSaveState: Boolean,
        autoTimeSync: Boolean,
        syncOnResume: Boolean,
        keepPowerOn: Boolean,
        overclockFactor: Double,
        fastForwardLimit: Int,
    ): String?

    private external fun nativeBuildInfo(): String
    private external fun nativeStart(): String?
    private external fun nativePause()
    private external fun nativeResume()
    private external fun nativeContinueInBackground()
    private external fun nativeStop()
    private external fun nativeIsActive(): Boolean
    private external fun nativeCopyLcdFrame(destination: ByteArray, lastSequence: Long): Long
    private external fun nativeSetKey(keyId: Int, pressed: Boolean)
    private external fun nativeSetAudioAvailable(available: Boolean)
    private external fun nativeSetFastForward(enabled: Boolean)
    private external fun nativeFastForwardMultiplier(): Double
    private external fun nativeRequestReset()
    private external fun nativeRequestSave(includeFlash: Boolean, includeState: Boolean)
    private external fun nativeRequestLoad(includeFlash: Boolean, includeState: Boolean): Boolean
    private external fun nativeStartImport(sourcePath: String, deviceNameGbk: ByteArray): String?
    private external fun nativeImportStatus(): String

    private var sdlAudioInitialized = false
}
