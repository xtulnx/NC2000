package io.github.wangyu.nc2000.launcher

import io.github.wangyu.nc2000.controls.ControlScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchProfileJsonTest {
    @Test
    fun controlSceneReferenceRoundTrips() {
        val original = LaunchProfile.defaultNc1020().copy(
            controlSceneId = ControlScene.DEFAULT_GAME_SCENE_ID,
        )

        val decoded = LaunchProfileJson.decode(LaunchProfileJson.encode(listOf(original))).single()

        assertEquals(LaunchProfile.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(ControlScene.DEFAULT_GAME_SCENE_ID, decoded.controlSceneId)
    }

    @Test
    fun oldProfileWithoutControlSceneUsesDefaultKeyboard() {
        val decoded = LaunchProfileJson.decode(
            """[{"schemaVersion":1,"id":"old","name":"旧入口","model":"NC1020"}]""",
        ).single()

        assertEquals(LaunchProfile.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertNull(decoded.controlSceneId)
        assertEquals(false, decoded.features.keepPowerOn)
        assertEquals(false, decoded.features.quickSaveFlash)
        assertEquals(true, decoded.features.quickSaveState)
    }

    @Test
    fun quickSaveStorageSelectionRoundTrips() {
        val original = LaunchProfile.defaultNc1020().copy(
            features = EmulatorFeatures(
                quickSaveFlash = true,
                quickSaveState = false,
            ),
        )

        val decoded = LaunchProfileJson.decode(LaunchProfileJson.encode(listOf(original))).single()

        assertEquals(true, decoded.features.quickSaveFlash)
        assertEquals(false, decoded.features.quickSaveState)
    }

    @Test
    fun internallySavedRuntimeStateDoesNotRequireAnImportedStateDocument() {
        val profile = LaunchProfile.defaultNc1020().copy(
            firmware = FirmwareFiles(
                romUri = "content://firmware/rom",
                norUri = "content://firmware/nor",
            ),
            features = EmulatorFeatures(loadState = true),
        )

        assertTrue(profile.validationErrors().isEmpty())
    }
}
