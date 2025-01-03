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

package com.geeksville.mesh.concurrent

import com.geeksville.mesh.android.Logging

/**
 * A deferred execution object (with various possible implementations)
 */
interface Continuation<in T> : Logging {
    abstract fun resume(res: Result<T>)

    // syntactic sugar

    fun resumeSuccess(res: T) = resume(Result.success(res))
    fun resumeWithException(ex: Throwable) = try {
        resume(Result.failure(ex))
    } catch (ex: Throwable) {
        // errormsg("Ignoring $ex while resuming, because we are the ones who threw it")
        throw ex
    }
}

/**
 * An async continuation that just calls a callback when the result is available
 */
class CallbackContinuation<in T>(private val cb: (Result<T>) -> Unit) : Continuation<T> {
    override fun resume(res: Result<T>) = cb(res)
}

/**
 * This is a blocking/threaded version of coroutine Continuation
 *
 * A little bit ugly, but the coroutine version has a nasty internal bug that showed up
 * in my SyncBluetoothDevice so I needed a quick workaround.
 */
class SyncContinuation<T> : Continuation<T> {

    private val mbox = java.lang.Object()
    private var result: Result<T>? = null

    override fun resume(res: Result<T>) {
        synchronized(mbox) {
            result = res
            mbox.notify()
        }
    }

    // Wait for the result (or throw an exception)
    fun await(timeoutMsecs: Long = 0): T {
        synchronized(mbox) {
            val startT = System.currentTimeMillis()
            while (result == null) {
                mbox.wait(timeoutMsecs)

                if (timeoutMsecs > 0 && ((System.currentTimeMillis() - startT) >= timeoutMsecs)) {
                    throw Exception("SyncContinuation timeout")
                }
            }

            val r = result
            if (r != null) {
                return r.getOrThrow()
            } else {
                throw Exception("This shouldn't happen")
            }
        }
    }
}

/**
 * Calls an init function which is responsible for saving our continuation so that some
 * other thread can call resume or resume with exception.
 *
 * Essentially this is a blocking version of the (buggy) coroutine suspendCoroutine
 */
fun <T> suspend(timeoutMsecs: Long = -1, initfn: (SyncContinuation<T>) -> Unit): T {
    val cont = SyncContinuation<T>()

    // First call the init funct
    initfn(cont)

    // Now wait for the continuation to finish
    return cont.await(timeoutMsecs)
}
