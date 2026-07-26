package io.github.wangyu.nc2000.emulator

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import io.github.wangyu.nc2000.MainActivity
import io.github.wangyu.nc2000.R
import io.github.wangyu.nc2000.nativebridge.NativeBridge
import io.github.wangyu.nc2000.nativebridge.NativeLaunchConfig

/**
 * Each concrete service has its own Android process.  The native emulator core
 * keeps global state, so process isolation is required for truly concurrent
 * emulator instances.
 */
abstract class EmulatorForegroundService(
    private val slot: Int,
) : Service() {
    private var profileId = ""
    private var wakeLock: PowerManager.WakeLock? = null

    private val binder = object : IEmulatorSession.Stub() {
        override fun configure(
            profileId: String,
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
        ): String? {
            if (NativeBridge.isActive()) {
                return "模拟器实例 #$slot 正在运行"
            }
            val error = NativeBridge.configure(
                NativeLaunchConfig(
                    model = model,
                    romPath = romPath,
                    norPath = norPath,
                    nandPath = nandPath,
                    nand0Path = nand0Path,
                    statePath = statePath,
                    loadState = loadState,
                    autoSaveFlash = autoSaveFlash,
                    autoSaveState = autoSaveState,
                    autoTimeSync = autoTimeSync,
                    syncOnResume = syncOnResume,
                    keepPowerOn = keepPowerOn,
                    overclockFactor = overclockFactor,
                    fastForwardLimit = fastForwardLimit,
                ),
            )
            if (error == null) this@EmulatorForegroundService.profileId = profileId
            return error
        }

        override fun start(): String? = NativeBridge.start().also { error ->
            if (error == null) {
                releaseWakeLock()
                updateNotification("正在前台运行")
            }
        }

        override fun pause() {
            NativeBridge.pause()
            releaseWakeLock()
            updateNotification("已在后台暂停 · 返回后继续")
        }

        override fun resume() {
            NativeBridge.resume()
            releaseWakeLock()
            updateNotification("正在前台运行")
        }

        override fun continueInBackground() {
            NativeBridge.continueInBackground()
            acquireWakeLock()
            updateNotification("正在后台持续运行 · 耗电较高")
        }

        override fun stop() {
            NativeBridge.stop()
            releaseWakeLock()
            profileId = ""
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        override fun copyLcdFrame(destination: ByteArray, lastSequence: Long): Long =
            NativeBridge.copyLcdFrame(destination, lastSequence)

        override fun setKey(keyId: Int, pressed: Boolean) = NativeBridge.setKey(keyId, pressed)
        override fun setFastForward(enabled: Boolean) = NativeBridge.setFastForward(enabled)
        override fun fastForwardMultiplier(): Double = NativeBridge.fastForwardMultiplier()
        override fun requestReset() = NativeBridge.requestReset()
        override fun requestSave(includeFlash: Boolean, includeState: Boolean) =
            NativeBridge.requestSave(includeFlash, includeState)
        override fun requestLoad(includeFlash: Boolean, includeState: Boolean): Boolean =
            NativeBridge.requestLoad(includeFlash, includeState)
        override fun startImport(sourcePath: String, deviceNameGbk: ByteArray): String? =
            NativeBridge.startImport(sourcePath, deviceNameGbk)
        override fun importStatus(): String = NativeBridge.importStatus()
        override fun profileId(): String = profileId
        override fun isActive(): Boolean = NativeBridge.isActive()
        override fun buildInfo(): String = NativeBridge.buildInfo()
    }

    override fun onCreate() {
        super.onCreate()
        NativeBridge.initializeSdlAudio(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification("正在准备启动")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        NativeBridge.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "${applicationContext.packageName}:emulator_$slot",
            )
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        if (!lock.isHeld) lock.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "模拟器运行中",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持模拟器在后台继续运行"
                setShowBadge(false)
            },
        )
    }

    private fun updateNotification(status: String) {
        val openApp = PendingIntent.getActivity(
            this,
            slot,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startForeground(
            NOTIFICATION_ID_BASE + slot,
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle("NC2000 Emulator #$slot")
                .setContentText(status)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "emulator_running"
        const val NOTIFICATION_ID_BASE = 2000
    }
}

class EmulatorSession1Service : EmulatorForegroundService(slot = 1)
class EmulatorSession2Service : EmulatorForegroundService(slot = 2)
class EmulatorSession3Service : EmulatorForegroundService(slot = 3)
class EmulatorSession4Service : EmulatorForegroundService(slot = 4)
