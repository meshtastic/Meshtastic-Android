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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException

object Exceptions {
    /** Set by the application to provide a custom crash reporting implementation. */
    var reporter: ((Throwable, String?, String?) -> Unit)? = null

    /**
     * Report an exception to the configured reporter (if any) after logging it.
     *
     * @param exception The exception to report.
     * @param tag An optional tag for the report.
     * @param message An optional message providing context.
     */
    fun report(exception: Throwable, tag: String? = null, message: String? = null) {
        // Log locally first
        Logger.e(exception) { "Exceptions.report: ${tag ?: "no-tag"} ${message ?: "no-message"}" }
        reporter?.invoke(exception, tag, message)
    }
}

/** Wraps and discards exceptions, optionally logging them. */
fun ignoreException(silent: Boolean = false, inner: () -> Unit) {
    try {
        inner()
    } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
        if (!silent) {
            Logger.w(ex) { "Ignoring exception" }
        }
    }
}

/** Suspend-compatible variant of [ignoreException]. Re-throws [CancellationException]. */
suspend fun ignoreExceptionSuspend(silent: Boolean = false, inner: suspend () -> Unit) {
    try {
        inner()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
        if (!silent) {
            Logger.w(ex) { "Ignoring exception" }
        }
    }
}

/**
 * Wraps and discards exceptions, but reports them to the crash reporter before logging. Use this for operations that
 * should not crash the process but are still unexpected.
 */
fun exceptionReporter(inner: () -> Unit) {
    try {
        inner()
    } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
        Exceptions.report(ex, "exceptionReporter", "Uncaught Exception")
    }
}

/**
 * Like [kotlin.runCatching], but re-throws [CancellationException] to preserve structured concurrency. Use this instead
 * of [runCatching] in coroutine contexts.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> safeCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Like [kotlin.runCatching] receiver variant, but re-throws [CancellationException]. */
@Suppress("TooGenericExceptionCaught")
inline fun <T, R> T.safeCatching(block: T.() -> R): Result<R> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Like [safeCatching] but also catches JVM [Error]s (e.g. [ExceptionInInitializerError] raised by compose-resources'
 * lazy skiko initialization on the desktop JVM test classpath). Still re-throws [CancellationException] so structured
 * concurrency is preserved. Use when the block invokes code whose failure modes include static-initializer errors and
 * the caller only needs a best-effort fallback.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> safeCatchingAll(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.failure(t)
}
