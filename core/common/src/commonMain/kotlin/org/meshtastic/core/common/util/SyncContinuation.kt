/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.common.util

/** A deferred execution object (with various possible implementations) */
interface Continuation<in T> {
    fun resume(res: Result<T>)

    /** Syntactic sugar for resuming with success. */
    fun resumeSuccess(res: T) = resume(Result.success(res))

    /** Syntactic sugar for resuming with failure. */
    fun resumeWithException(ex: Throwable) = resume(Result.failure(ex))
}

/** An async continuation that calls a callback when the result is available. */
class CallbackContinuation<in T>(private val cb: (Result<T>) -> Unit) : Continuation<T> {
    override fun resume(res: Result<T>) = cb(res)
}

/**
 * A blocking version of coroutine Continuation using traditional threading primitives.
 *
 * This is useful in contexts where coroutine suspension is not desirable or when bridging with legacy threaded code.
 */
class SyncContinuation<T> : Continuation<T> {

    private val lock = java.util.concurrent.locks.ReentrantLock()
    private val condition = lock.newCondition()
    private var result: Result<T>? = null

    override fun resume(res: Result<T>) {
        lock.lock()
        try {
            result = res
            condition.signal()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Blocks the current thread until the result is available or the timeout expires.
     *
     * @param timeoutMsecs Maximum time to wait in milliseconds. If 0, waits indefinitely.
     * @return The result of the operation.
     * @throws IllegalStateException if a timeout occurs or if an internal error happens.
     */
    @Suppress("NestedBlockDepth")
    fun await(timeoutMsecs: Long = 0): T {
        lock.lock()
        try {
            val startT = nowMillis
            while (result == null) {
                if (timeoutMsecs > 0) {
                    val remaining = timeoutMsecs - (nowMillis - startT)
                    check(remaining > 0) { "SyncContinuation timeout" }
                    condition.await(remaining, java.util.concurrent.TimeUnit.MILLISECONDS)
                } else {
                    condition.await()
                }
            }

            val r = result
            checkNotNull(r) { "Unexpected null result in SyncContinuation" }
            return r.getOrThrow()
        } finally {
            lock.unlock()
        }
    }
}

/**
 * Calls an [initfn] that is responsible for starting an operation and saving the [SyncContinuation]. Then blocks the
 * current thread until the operation completes or times out.
 *
 * Essentially a blocking version of [kotlinx.coroutines.suspendCancellableCoroutine].
 */
fun <T> suspend(timeoutMsecs: Long = -1, initfn: (SyncContinuation<T>) -> Unit): T {
    val cont = SyncContinuation<T>()
    initfn(cont)
    return cont.await(timeoutMsecs)
}
