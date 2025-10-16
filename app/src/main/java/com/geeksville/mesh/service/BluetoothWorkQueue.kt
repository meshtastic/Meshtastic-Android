/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.service

import android.bluetooth.BluetoothGatt
import com.geeksville.mesh.concurrent.Continuation
import com.geeksville.mesh.logAssert
import com.geeksville.mesh.util.exceptionReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** A data class representing a schedulable unit of Bluetooth work. */
internal data class BluetoothWorkItem(
    val tag: String,
    val completion: Continuation<*>,
    val timeoutMillis: Long = 0, // If we want to timeout this operation at a certain time, use a non zero value
    private val startWorkFn: () -> Boolean,
) {
    /** Start running a queued bit of work, return true for success or false for fatal bluetooth error. */
    fun startWork(): Boolean {
        Timber.d("Starting work: $tag")
        return startWorkFn()
    }

    /** Connection work items are treated specially. */
    fun isConnect(): Boolean = tag == "connect" || tag == "reconnect"
}

/** Manages a queue of bluetooth operations to ensure that only one is in flight at a time. */
internal class BluetoothWorkQueue {
    @Volatile
    var currentWork: BluetoothWorkItem? = null
        private set

    private val workQueue = mutableListOf<BluetoothWorkItem>()

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var activeTimeout: Job? = null

    companion object {
        /** Our own custom BLE status code for timeouts */
        private const val STATUS_TIMEOUT = 4404
    }

    var isSettingMtu: Boolean = false

    // / If we have work we can do, start doing it.
    private fun startNewWork() {
        logAssert(currentWork == null)

        if (workQueue.isNotEmpty()) {
            val newWork = workQueue.removeAt(0)
            currentWork = newWork

            if (newWork.timeoutMillis > 0) {
                activeTimeout =
                    serviceScope.launch {
                        delay(newWork.timeoutMillis)
                        Timber.e("Failsafe BLE timer for ${newWork.tag} expired!")
                        completeWork(STATUS_TIMEOUT, Unit) // Throw an exception in that work
                    }
            }
            isSettingMtu = false // Most work is not doing MTU stuff, the work that is will re set this flag
            if (!newWork.startWork()) {
                completeWork(STATUS_TIMEOUT, Unit)
            }
        }
    }

    fun <T> queueWork(tag: String, cont: Continuation<T>, timeout: Long, initFn: () -> Boolean) {
        val workItem = BluetoothWorkItem(tag, cont, timeout, initFn)

        synchronized(workQueue) {
            Timber.d("Enqueuing work: ${workItem.tag}")
            workQueue.add(workItem)

            // if we don't have any outstanding operations, run first item in queue
            if (currentWork == null) startNewWork()
        }
    }

    /** Stop any current work */
    private fun stopCurrentWork() {
        activeTimeout?.cancel()
        activeTimeout = null
        currentWork = null
    }

    /** Called from our big GATT callback, completes the current job and then schedules a new one */
    fun <T : Any> completeWork(status: Int, res: T) {
        exceptionReporter {
            // We might unexpectedly fail inside here, but we don't want to pass that exception back up to the bluetooth
            // GATT layer

            val work =
                synchronized(workQueue) {
                    currentWork.also {
                        if (it != null) {
                            stopCurrentWork()
                            startNewWork()
                        }
                    }
                }

            if (work == null) {
                Timber.w("Work completed, but it was already killed (possibly by timeout). status=$status, res=$res")
                return@exceptionReporter
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                work.completion.resumeWithException(
                    SafeBluetooth.BLEStatusException(status, "Bluetooth status=$status while doing ${work.tag}"),
                )
            } else {
                @Suppress("UNCHECKED_CAST")
                work.completion.resume(Result.success(res) as Result<Nothing>)
            }
        }
    }

    /** Something went wrong, abort all queued */
    fun failAllWork(ex: Exception) {
        synchronized(workQueue) {
            Timber.w("Failing ${workQueue.size} works, because ${ex.message}")
            workQueue.forEach {
                @Suppress("TooGenericExceptionCaught")
                try {
                    it.completion.resumeWithException(ex)
                } catch (e: Exception) {
                    Timber.e(e, "Exception while failing work item ${it.tag}")
                }
            }
            workQueue.clear()
            stopCurrentWork()
        }
    }
}
