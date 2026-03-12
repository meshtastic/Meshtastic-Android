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
