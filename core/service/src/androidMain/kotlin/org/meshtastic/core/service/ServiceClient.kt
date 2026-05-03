/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.IInterface
import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import org.meshtastic.core.common.util.exceptionReporter
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BindFailedException : Exception("bindService failed")

/**
 * A generic helper for binding to an Android Service via AIDL. Handles connection lifecycle, thread safety for initial
 * binding, and automatic retry for common race conditions.
 *
 * @param T The type of the AIDL interface.
 * @param stubFactory A factory function to convert an [IBinder] to the interface type.
 */
open class ServiceClient<T : IInterface>(private val stubFactory: (IBinder) -> T) : Closeable {

    private companion object {
        const val BIND_RETRY_DELAY_MS = 500L
    }

    /** The currently bound service instance, or null if not connected. */
    var serviceP: T? = null

    /**
     * Returns the bound service instance. If not currently connected, this will block the current thread until the
     * connection is established.
     *
     * @throws IllegalStateException If [connect] has not been called.
     * @throws IllegalStateException If the service is not bound after waiting.
     */
    val service: T
        get() {
            waitConnect()
            return checkNotNull(serviceP) { "Service not bound" }
        }

    private var context: Context? = null
    private var isClosed = true

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    /**
     * Blocks the current thread until the service is connected.
     *
     * @throws IllegalStateException If [connect] has not been called.
     */
    fun waitConnect() {
        lock.withLock {
            check(context != null) { "Connect must be called before waitConnect" }

            if (serviceP == null) {
                condition.await()
            }
        }
    }

    /**
     * Initiates a binding to the service.
     *
     * @param c The context to use for binding.
     * @param intent The intent used to identify the service.
     * @param flags Binding flags (e.g., [Context.BIND_AUTO_CREATE]).
     * @throws BindFailedException If the initial bind call fails twice.
     */
    suspend fun connect(c: Context, intent: Intent, flags: Int) {
        context = c
        if (isClosed) {
            isClosed = false
            if (!c.bindService(intent, connection, flags)) {
                // Handle potential race condition on quick re-bind
                Logger.w { "Initial bind failed, retrying after delay..." }
                delay(BIND_RETRY_DELAY_MS)
                if (!c.bindService(intent, connection, flags)) {
                    throw BindFailedException()
                }
            }
        } else {
            Logger.w { "Ignoring rebind attempt for already active service connection" }
        }
    }

    override fun close() {
        isClosed = true
        try {
            context?.unbindService(connection)
        } catch (ex: IllegalArgumentException) {
            Logger.w(ex) { "Ignoring error during unbind: service might have already been cleaned up" }
        }
        serviceP = null
        context = null
    }

    /** Called on the main thread when the service is connected. */
    open fun onConnected(service: T) {}

    /** Called on the main thread when the service connection is lost. */
    open fun onDisconnected() {}

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) = exceptionReporter {
                if (!isClosed) {
                    val s = stubFactory(binder)
                    serviceP = s
                    onConnected(s)

                    lock.withLock { condition.signalAll() }
                } else {
                    Logger.w { "Service connected after close was called; ignoring stale connection" }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = exceptionReporter {
                serviceP = null
                onDisconnected()
            }
        }
}
