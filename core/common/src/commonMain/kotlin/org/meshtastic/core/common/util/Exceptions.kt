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
        Logger.e(exception) { "Exceptions.report: $tag $message" }
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

/** Suspend-compatible variant of [ignoreException]. */
suspend fun ignoreExceptionSuspend(silent: Boolean = false, inner: suspend () -> Unit) {
    try {
        inner()
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
