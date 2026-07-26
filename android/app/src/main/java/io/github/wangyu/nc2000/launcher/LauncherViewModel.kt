package io.github.wangyu.nc2000.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.wangyu.nc2000.controls.ControlScene
import io.github.wangyu.nc2000.controls.ControlSceneStore
import io.github.wangyu.nc2000.emulator.EmulatorSessionManager
import io.github.wangyu.nc2000.emulator.RunningEmulatorSession
import io.github.wangyu.nc2000.nativebridge.NativeBridge
import io.github.wangyu.nc2000.nativebridge.NativeLaunchConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LauncherViewModel(
    private val store: LaunchProfileStore,
    private val controlSceneStore: ControlSceneStore,
    private val firmwareStager: FirmwareStager,
    private val sessionManager: EmulatorSessionManager,
) : ViewModel() {
    val profiles: StateFlow<List<LaunchProfile>> = store.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = listOf(LaunchProfile.defaultNc1020()),
    )

    val controlScenes: StateFlow<List<ControlScene>> = controlSceneStore.scenes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = listOf(ControlScene.defaultGameOverlay()),
    )

    val message = MutableStateFlow<String?>(null)
    val runningSessions = MutableStateFlow<List<RunningEmulatorSession>>(emptyList())
    val displayedSession = MutableStateFlow<RunningEmulatorSession?>(null)
    private val launchingSlots = MutableStateFlow<Set<Int>>(emptySet())
    private var sessionInventoryReady = false
    private var appInForeground = true
    private val appLifecycleMutex = Mutex()

    init {
        viewModelScope.launch {
            store.initialize()
            controlSceneStore.initialize()
            runningSessions.value = withContext(Dispatchers.IO) { sessionManager.runningSessions() }
            sessionInventoryReady = true
            applyAppLifecyclePolicy()
        }
    }

    fun saveProfile(profile: LaunchProfile) {
        updateProfiles { current ->
            val index = current.indexOfFirst { it.id == profile.id }
            if (index < 0) current + profile else current.toMutableList().apply { set(index, profile) }
        }
    }

    fun duplicateProfile(profile: LaunchProfile) {
        saveProfile(
            profile.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${profile.name} 副本",
            ),
        )
    }

    fun deleteProfile(profileId: String) {
        updateProfiles { current -> current.filterNot { it.id == profileId } }
    }

    fun moveProfile(profileId: String, offset: Int) {
        updateProfiles { current ->
            val from = current.indexOfFirst { it.id == profileId }
            if (from < 0) return@updateProfiles current
            val to = (from + offset).coerceIn(current.indices)
            if (from == to) return@updateProfiles current
            current.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
    }

    fun attachFirmware(profileId: String, documents: List<SelectedDocument>) {
        val profile = profiles.value.firstOrNull { it.id == profileId } ?: return
        var firmware = profile.firmware
        documents.forEach { document ->
            when (document.displayName.substringAfterLast('.', "").lowercase()) {
                "rom" -> firmware = firmware.copy(romUri = document.uri)
                "nor" -> firmware = firmware.copy(norUri = document.uri)
                "nand" -> firmware = firmware.copy(nandUri = document.uri)
                "nand0" -> firmware = firmware.copy(nand0Uri = document.uri)
                "state" -> firmware = firmware.copy(stateUri = document.uri)
            }
        }
        saveProfile(profile.copy(firmware = firmware))
    }

    fun attachCustomIcon(profileId: String, uri: String) {
        val profile = profiles.value.firstOrNull { it.id == profileId } ?: return
        saveProfile(
            profile.copy(icon = ProfileIcon(ProfileIconKind.CUSTOM_URI, uri)),
        )
    }

    fun saveControlScene(scene: ControlScene) {
        val errors = scene.validationErrors()
        if (errors.isNotEmpty()) {
            message.value = errors.joinToString("；")
            return
        }
        viewModelScope.launch {
            val current = controlScenes.value
            val index = current.indexOfFirst { it.id == scene.id }
            val updated = if (index < 0) {
                current + scene
            } else {
                current.toMutableList().apply { set(index, scene) }
            }
            controlSceneStore.save(updated)
        }
    }

    fun deleteControlScene(sceneId: String) {
        viewModelScope.launch {
            controlSceneStore.save(controlScenes.value.filterNot { it.id == sceneId })
            store.save(
                profiles.value.map { profile ->
                    if (profile.controlSceneId == sceneId) profile.copy(controlSceneId = null)
                    else profile
                },
            )
        }
    }

    fun launch(profile: LaunchProfile) {
        if (!sessionInventoryReady) {
            message.value = "正在检查后台运行的模拟器…"
            return
        }
        if (launchingSlots.value.isNotEmpty()) {
            message.value = "正在启动模拟器，请稍候…"
            return
        }
        val existing = runningSessions.value.firstOrNull { it.profileId == profile.id }
        if (existing != null) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    sessionManager.activate(existing.slot, profile.id)
                    NativeBridge.resume()
                }
                displayedSession.value = existing
            }
            return
        }
        val errors = profile.validationErrors()
        if (errors.isNotEmpty()) {
            message.value = errors.joinToString("；")
            return
        }
        val slot = (1..EmulatorSessionManager.MAX_SESSIONS)
            .firstOrNull { candidate ->
                runningSessions.value.none { it.slot == candidate } &&
                    candidate !in launchingSlots.value
            }
        if (slot == null) {
            message.value = "最多可同时运行 ${EmulatorSessionManager.MAX_SESSIONS} 台模拟器"
            return
        }
        message.value = "正在准备模拟器 #$slot…"
        launchingSlots.value = launchingSlots.value + slot
        viewModelScope.launch {
            val error = try {
                runCatching {
                    val staged = firmwareStager.stage(profile)
                    withContext(Dispatchers.IO) { sessionManager.prepare(slot, profile.id) }
                    val configureError = NativeBridge.configure(
                        NativeLaunchConfig(
                            model = profile.model.name,
                            romPath = staged.romPath,
                            norPath = staged.norPath,
                            nandPath = staged.nandPath,
                            nand0Path = staged.nand0Path,
                            statePath = staged.statePath,
                            loadState = profile.features.loadState,
                            autoSaveFlash = profile.features.autoSaveFlash,
                            autoSaveState = profile.features.autoSaveState,
                            autoTimeSync = profile.features.autoTimeSync,
                            syncOnResume = profile.features.syncOnResume,
                            // Foreground execution must never stop merely due to
                            // input idleness. Background pause/continue is
                            // controlled separately at runtime.
                            keepPowerOn = true,
                            overclockFactor = profile.features.overclockFactor,
                            fastForwardLimit = profile.features.fastForwardLimit,
                        ),
                    )
                    if (configureError != null) error(configureError)
                    val startError = NativeBridge.start()
                    if (startError != null) error(startError)
                    null
                }.getOrElse { cause -> cause.message ?: "模拟器启动失败" }
            } finally {
                launchingSlots.value = launchingSlots.value - slot
            }
            if (error == null) {
                val session = RunningEmulatorSession(
                    slot = slot,
                    profileId = profile.id,
                    backgroundContinues = profile.features.keepPowerOn,
                )
                message.value = null
                runningSessions.value = runningSessions.value + session
                displayedSession.value = session
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { sessionManager.stopIfOwned(slot, profile.id) }
                }
                message.value = error
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun updateBackgroundPolicy(backgroundContinues: Boolean) {
        val displayed = displayedSession.value ?: return
        val updated = displayed.copy(backgroundContinues = backgroundContinues)
        displayedSession.value = updated
        runningSessions.value = runningSessions.value.map { session ->
            if (session.slot == updated.slot) updated else session
        }
    }

    fun backgroundSession(backgroundContinues: Boolean) {
        updateBackgroundPolicy(backgroundContinues)
        // Returning to the launcher keeps the application visible, so the
        // emulator continues normally. The per-session policy is only used
        // when the whole application enters the background.
        NativeBridge.resume()
        displayedSession.value = null
    }

    fun onAppForegrounded() {
        appInForeground = true
        applyAppLifecyclePolicy()
    }

    fun onAppBackgrounded() {
        appInForeground = false
        applyAppLifecyclePolicy()
    }

    fun sessionStopped() {
        displayedSession.value?.let { stopped ->
            runningSessions.value = runningSessions.value.filterNot { it.slot == stopped.slot }
        }
        displayedSession.value = null
    }

    override fun onCleared() {
        super.onCleared()
    }

    private fun applyAppLifecyclePolicy() {
        if (!sessionInventoryReady) return
        val sessions = runningSessions.value
        if (sessions.isEmpty()) return
        val profilesById = profiles.value.associateBy { it.id }
        val foreground = appInForeground
        val displayed = displayedSession.value
        viewModelScope.launch {
            appLifecycleMutex.withLock {
                withContext(Dispatchers.IO) {
                    sessions.forEach { session ->
                        try {
                            sessionManager.activate(session.slot, session.profileId)
                            if (foreground) {
                                NativeBridge.resume()
                            } else if (
                                session.backgroundContinues
                                    ?: profilesById[session.profileId]?.features?.keepPowerOn == true
                            ) {
                                NativeBridge.continueInBackground()
                            } else {
                                NativeBridge.pause()
                            }
                        } catch (_: Exception) {
                            // A service can disappear between inventory and a lifecycle event.
                        }
                    }
                    displayed?.let { session ->
                        runCatching { sessionManager.activate(session.slot, session.profileId) }
                    }
                }
            }
        }
    }

    private fun updateProfiles(transform: (List<LaunchProfile>) -> List<LaunchProfile>) {
        viewModelScope.launch { store.save(transform(profiles.value)) }
    }

    class Factory(
        private val store: LaunchProfileStore,
        private val controlSceneStore: ControlSceneStore,
        private val firmwareStager: FirmwareStager,
        private val sessionManager: EmulatorSessionManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LauncherViewModel::class.java))
            return LauncherViewModel(store, controlSceneStore, firmwareStager, sessionManager) as T
        }
    }
}
