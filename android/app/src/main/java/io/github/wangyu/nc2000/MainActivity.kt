package io.github.wangyu.nc2000

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wangyu.nc2000.controls.ControlSceneStore
import io.github.wangyu.nc2000.launcher.LaunchProfileStore
import io.github.wangyu.nc2000.launcher.FirmwareStager
import io.github.wangyu.nc2000.launcher.LauncherScreen
import io.github.wangyu.nc2000.launcher.LauncherViewModel
import io.github.wangyu.nc2000.launcher.SelectedDocument
import io.github.wangyu.nc2000.emulator.EmulatorScreen
import io.github.wangyu.nc2000.emulator.EmulatorSessionManager
import io.github.wangyu.nc2000.nativebridge.NativeBridge
import io.github.wangyu.nc2000.ui.theme.NC2000Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NC2000Theme {
                val store = remember { LaunchProfileStore(applicationContext) }
                val controlSceneStore = remember { ControlSceneStore(applicationContext) }
                val launcherViewModel: LauncherViewModel = viewModel(
                    factory = LauncherViewModel.Factory(
                        store = store,
                        controlSceneStore = controlSceneStore,
                        firmwareStager = remember { FirmwareStager(applicationContext) },
                        sessionManager = remember { EmulatorSessionManager(applicationContext) },
                    ),
                )
                val profiles by launcherViewModel.profiles.collectAsStateWithLifecycle()
                val controlScenes by launcherViewModel.controlScenes.collectAsStateWithLifecycle()
                val message by launcherViewModel.message.collectAsStateWithLifecycle()
                val runningSessions by launcherViewModel.runningSessions.collectAsStateWithLifecycle()
                val displayedSession by launcherViewModel.displayedSession.collectAsStateWithLifecycle()
                val nativeBuildInfo = remember { NativeBridge.buildInfo() }
                val lifecycleOwner = LocalLifecycleOwner.current
                val currentLauncherViewModel by rememberUpdatedState(launcherViewModel)

                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> currentLauncherViewModel.onAppForegrounded()
                            Lifecycle.Event.ON_STOP -> currentLauncherViewModel.onAppBackgrounded()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                var pendingFirmwareProfileId by remember { mutableStateOf<String?>(null) }
                var pendingIconProfileId by remember { mutableStateOf<String?>(null) }

                val firmwarePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    val profileId = pendingFirmwareProfileId ?: return@rememberLauncherForActivityResult
                    val documents = uris.map { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                        SelectedDocument(
                            displayName = queryDisplayName(uri) ?: uri.lastPathSegment.orEmpty(),
                            uri = uri.toString(),
                        )
                    }
                    launcherViewModel.attachFirmware(profileId, documents)
                    pendingFirmwareProfileId = null
                }

                val iconPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    val profileId = pendingIconProfileId
                    if (uri != null && profileId != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                        launcherViewModel.attachCustomIcon(profileId, uri.toString())
                    }
                    pendingIconProfileId = null
                }

                val displayedProfile = displayedSession?.let { session ->
                    profiles.firstOrNull { it.id == session.profileId }
                }
                if (displayedProfile == null) {
                    LauncherScreen(
                        profiles = profiles,
                        controlScenes = controlScenes,
                        nativeBuildInfo = nativeBuildInfo,
                        message = message,
                        runningSessions = runningSessions,
                        onMessageShown = launcherViewModel::clearMessage,
                        onSaveProfile = launcherViewModel::saveProfile,
                        onDuplicateProfile = launcherViewModel::duplicateProfile,
                        onDeleteProfile = launcherViewModel::deleteProfile,
                        onMoveProfile = launcherViewModel::moveProfile,
                        onPickFirmware = { profileId ->
                            pendingFirmwareProfileId = profileId
                            firmwarePicker.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        onPickIcon = { profileId ->
                            pendingIconProfileId = profileId
                            iconPicker.launch(arrayOf("image/*"))
                        },
                        onSaveControlScene = launcherViewModel::saveControlScene,
                        onDeleteControlScene = launcherViewModel::deleteControlScene,
                        onLaunch = launcherViewModel::launch,
                    )
                } else {
                    EmulatorScreen(
                        title = "${displayedProfile.name} · #${displayedSession!!.slot}",
                        profileId = displayedProfile.id,
                        firmware = displayedProfile.firmware,
                        controlScene = controlScenes.firstOrNull {
                            it.id == displayedProfile.controlSceneId
                        },
                        autoSaveFlash = displayedProfile.features.autoSaveFlash,
                        autoSaveState = displayedProfile.features.autoSaveState,
                        quickSaveFlash = displayedProfile.features.quickSaveFlash,
                        quickSaveState = displayedProfile.features.quickSaveState,
                        initialBackgroundContinues = displayedSession!!.backgroundContinues
                            ?: displayedProfile.features.keepPowerOn,
                        onBackgroundPolicyChange = launcherViewModel::updateBackgroundPolicy,
                        onBackground = launcherViewModel::backgroundSession,
                        onStop = launcherViewModel::sessionStopped,
                    )
                }
            }
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)
        }
}
