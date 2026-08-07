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
package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Linearizes admitted transport operations with terminal, idempotent teardown. */
internal class TransportLifecycleGate(
    private val label: String,
    private val operationDrainTimeout: Duration = OPERATION_DRAIN_TIMEOUT,
    private val teardownTimeout: Duration = TEARDOWN_TIMEOUT,
) {
    internal companion object {
        val OPERATION_DRAIN_TIMEOUT = 15.seconds
        val TEARDOWN_TIMEOUT = 15.seconds
    }

    private val lock = SynchronizedObject()
    private val closed = atomic(false)
    private var admittedOperations = 0
    private var operationDrainWaiter: CompletableDeferred<Unit>? = null
    private var closeCompletion: CompletableDeferred<Boolean>? = null

    val isClosed: Boolean
        get() = closed.value

    /** One admitted operation. Release exactly once when the externally visible work has actually finished. */
    internal class OperationLease internal constructor(private val releaseAction: () -> Unit) {
        private val released = atomic(false)

        fun release() {
            if (released.compareAndSet(expect = false, update = true)) releaseAction()
        }
    }

    /** Acquires an operation lease, or returns `null` once close has begun. */
    fun tryAcquire(): OperationLease? = synchronized(lock) {
        if (closed.value) return@synchronized null
        admittedOperations++
        OperationLease(::releaseOperation)
    }

    /** Runs [block] outside the gate lock after admission, or returns `null` once close has begun. */
    fun <T : Any> runIfOpen(block: () -> T): T? {
        val lease = tryAcquire() ?: return null
        return try {
            block()
        } finally {
            lease.release()
        }
    }

    /**
     * Closes admission, drains admitted work, and cooperatively bounds [teardown], which executes exactly once.
     *
     * [NonCancellable] keeps callers from abandoning shared close ownership, while the nested timeout can still cancel
     * cooperative teardown work. Returns `true` only when both the admitted-operation drain and teardown complete
     * within their bounds. A teardown failure is recorded on the shared completion and rethrown to every closer; the
     * gate is deliberately not reopened or retried after a poisoned teardown.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun close(teardown: suspend () -> Unit = {}): Boolean = withContext(NonCancellable) {
        val plan =
            synchronized(lock) {
                if (closed.value) {
                    ClosePlan(owner = false, completion = checkNotNull(closeCompletion), operationDrain = null)
                } else {
                    closed.value = true
                    val completion = CompletableDeferred<Boolean>().also { closeCompletion = it }
                    val operationDrain =
                        if (admittedOperations == 0) {
                            null
                        } else {
                            operationDrainWaiter ?: CompletableDeferred<Unit>().also { operationDrainWaiter = it }
                        }
                    ClosePlan(owner = true, completion = completion, operationDrain = operationDrain)
                }
            }
        if (!plan.owner) {
            return@withContext plan.completion.await()
        }
        try {
            val drained =
                plan.operationDrain == null ||
                    withTimeoutOrNull(operationDrainTimeout) {
                        plan.operationDrain.await()
                        true
                    } == true
            if (!drained) {
                Logger.w {
                    "$label transport close timed out after $operationDrainTimeout " +
                        "while draining admitted operations"
                }
            }
            val tornDown =
                withTimeoutOrNull(teardownTimeout) {
                    teardown()
                    true
                } == true
            if (!tornDown) Logger.w { "$label transport teardown timed out after $teardownTimeout" }
            val completed = drained && tornDown
            plan.completion.complete(completed)
            completed
        } catch (failure: Throwable) {
            plan.completion.completeExceptionally(failure)
            throw failure
        } finally {
            if (!plan.completion.isCompleted) plan.completion.complete(false)
        }
    }

    private data class ClosePlan(
        val owner: Boolean,
        val completion: CompletableDeferred<Boolean>,
        val operationDrain: CompletableDeferred<Unit>?,
    )

    private fun releaseOperation() {
        val waiter =
            synchronized(lock) {
                if (admittedOperations <= 0) {
                    Logger.e { "$label transport lifecycle operation count underflow" }
                    null
                } else {
                    admittedOperations--
                    if (admittedOperations == 0) {
                        operationDrainWaiter.also { operationDrainWaiter = null }
                    } else {
                        null
                    }
                }
            }
        waiter?.complete(Unit)
    }
}
