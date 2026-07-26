package io.github.wangyu.nc2000.emulator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.github.wangyu.nc2000.nativebridge.NativeBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RunningEmulatorSession(
    val slot: Int,
    val profileId: String,
    val backgroundContinues: Boolean? = null,
)

/** Binds the launcher process to independently isolated emulator processes. */
class EmulatorSessionManager(private val context: Context) {
    companion object {
        const val MAX_SESSIONS = 4
    }

    private val mutex = Mutex()
    private val clients = mutableMapOf<Int, IEmulatorSession>()
    private val pendingClients = mutableMapOf<Int, CompletableDeferred<IEmulatorSession>>()

    suspend fun prepare(slot: Int, profileId: String): IEmulatorSession {
        require(slot in 1..MAX_SESSIONS)
        val client = client(slot)
        if (client.isActive) {
            throw IllegalStateException("模拟器实例 #$slot 正在运行")
        }
        NativeBridge.attach(client, profileId)
        context.startForegroundService(Intent(context, serviceClass(slot)))
        return client
    }

    suspend fun activate(slot: Int, profileId: String = ""): IEmulatorSession {
        val client = client(slot)
        NativeBridge.attach(client, profileId)
        return client
    }

    /** Stops only the instance that belongs to the failed launch attempt. */
    suspend fun stopIfOwned(slot: Int, profileId: String) {
        val client = client(slot)
        if (client.isActive && client.profileId() != profileId) return
        NativeBridge.attach(client, profileId)
        client.stop()
    }

    suspend fun runningSessions(): List<RunningEmulatorSession> = buildList {
        for (slot in 1..MAX_SESSIONS) {
            val client = client(slot)
            if (client.isActive) {
                val profileId = client.profileId()
                if (profileId.isNotBlank()) add(RunningEmulatorSession(slot, profileId))
            }
        }
    }

    private suspend fun client(slot: Int): IEmulatorSession {
        val deferred = mutex.withLock {
            clients[slot]?.let { existing ->
                CompletableDeferred<IEmulatorSession>().apply { complete(existing) }
            } ?: pendingClients[slot] ?: CompletableDeferred<IEmulatorSession>().also { pending ->
                pendingClients[slot] = pending
                val bound = context.bindService(
                    Intent(context, serviceClass(slot)),
                    connection(slot, pending),
                    Context.BIND_AUTO_CREATE,
                )
                if (!bound) {
                    pendingClients.remove(slot)
                    pending.completeExceptionally(
                        IllegalStateException("无法连接模拟器实例 #$slot"),
                    )
                }
            }
        }
        return deferred.await()
    }

    private fun connection(
        slot: Int,
        pending: CompletableDeferred<IEmulatorSession>,
    ) = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val client = IEmulatorSession.Stub.asInterface(service)
            synchronized(clients) { clients[slot] = client }
            pendingClients.remove(slot)
            pending.complete(client)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(clients) { clients.remove(slot) }
        }

        override fun onBindingDied(name: ComponentName) {
            synchronized(clients) { clients.remove(slot) }
        }

        override fun onNullBinding(name: ComponentName) {
            pendingClients.remove(slot)
            pending.completeExceptionally(IllegalStateException("模拟器实例 #$slot 不可用"))
        }
    }

    private fun serviceClass(slot: Int): Class<out EmulatorForegroundService> = when (slot) {
        1 -> EmulatorSession1Service::class.java
        2 -> EmulatorSession2Service::class.java
        3 -> EmulatorSession3Service::class.java
        4 -> EmulatorSession4Service::class.java
        else -> error("无效的模拟器实例编号：$slot")
    }
}
