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
import kotlinx.coroutines.CancellationException
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
     * Closes admission, cooperatively bounds [beforeDrain], drains admitted work, and then bounds [teardown].
     *
     * [beforeDrain] lets an owner stop an internal worker whose completion releases operation leases. Admission is
     * already closed before it runs, so callbacks caused by that preparation cannot escape the gate. Both phases
     * execute exactly once.
     *
     * [NonCancellable] keeps callers from abandoning shared close ownership, while the nested timeouts can still cancel
     * cooperative work. They cannot bound a teardown lambda that blocks a thread without a cancellation point, or a
     * nested [close] that has entered its own [NonCancellable] ownership. Owners that nest lifecycle closes must size
     * their outer timeout to include the nested drain/teardown budget, and potentially blocking platform work must
     * remain interruptible. Returns `true` only when preparation, the admitted-operation drain, and teardown all
     * complete within their bounds. A phase failure is recorded on the shared completion and rethrown to every closer;
     * the gate is deliberately not reopened or retried after a poisoned close.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun close(beforeDrain: suspend () -> Unit = {}, teardown: suspend () -> Unit = {}): Boolean =
        withContext(NonCancellable) {
            val plan = closePlan()
            if (!plan.owner) return@withContext plan.completion.await()

            try {
                val preparation = prepareForClose(beforeDrain)
                logPreparationResult(preparation)
                val drained = awaitOperationDrain(plan.operationDrain)
                val tornDown = runTeardown(teardown)
                (preparation as? ClosePreparation.Failed)?.failure?.let { throw it }

                val completed = preparation === ClosePreparation.Completed && drained && tornDown
                plan.completion.complete(completed)
                completed
            } catch (failure: Throwable) {
                plan.completion.completeExceptionally(failure)
                throw failure
            } finally {
                if (!plan.completion.isCompleted) plan.completion.complete(false)
            }
        }

    private fun closePlan(): ClosePlan = synchronized(lock) {
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

    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepareForClose(beforeDrain: suspend () -> Unit): ClosePreparation =
        withTimeoutOrNull(operationDrainTimeout) {
            try {
                beforeDrain()
                ClosePreparation.Completed
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                ClosePreparation.Failed(failure)
            }
        } ?: ClosePreparation.TimedOut

    private fun logPreparationResult(preparation: ClosePreparation) {
        when (preparation) {
            ClosePreparation.Completed -> Unit

            ClosePreparation.TimedOut ->
                Logger.w { "$label transport close preparation timed out after $operationDrainTimeout" }

            is ClosePreparation.Failed -> Logger.w(preparation.failure) { "$label transport close preparation failed" }
        }
    }

    private suspend fun awaitOperationDrain(operationDrain: CompletableDeferred<Unit>?): Boolean {
        val drained =
            operationDrain == null ||
                withTimeoutOrNull(operationDrainTimeout) {
                    operationDrain.await()
                    true
                } == true
        if (!drained) {
            Logger.w {
                "$label transport close timed out after $operationDrainTimeout while draining admitted operations"
            }
        }
        return drained
    }

    private suspend fun runTeardown(teardown: suspend () -> Unit): Boolean {
        val tornDown =
            withTimeoutOrNull(teardownTimeout) {
                teardown()
                true
            } == true
        if (!tornDown) Logger.w { "$label transport teardown timed out after $teardownTimeout" }
        return tornDown
    }

    private sealed interface ClosePreparation {
        data object Completed : ClosePreparation

        data object TimedOut : ClosePreparation

        data class Failed(val failure: Throwable) : ClosePreparation
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
                    operationDrainWaiter.also { operationDrainWaiter = null }
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
