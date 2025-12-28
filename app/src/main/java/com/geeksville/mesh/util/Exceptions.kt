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

package com.geeksville.mesh.util

import android.os.RemoteException
import android.util.Log
import co.touchlab.kermit.Logger

object Exceptions {
    // / Set in Application.onCreate
    var reporter: ((Throwable, String?, String?) -> Unit)? = null

    /**
     * Report an exception to our analytics provider (if installed - otherwise just log)
     *
     * After reporting return
     */
    fun report(exception: Throwable, tag: String? = null, message: String? = null) {
        Logger.e(exception) {
            "Exceptions.report: $tag $message"
        } // print the message to the log _before_ telling the crash reporter
        reporter?.let { r -> r(exception, tag, message) }
    }
}

/**
 * This wraps (and discards) exceptions, but first it reports them to our bug tracking system and prints a message to
 * the log.
 */
fun exceptionReporter(inner: () -> Unit) {
    try {
        inner()
    } catch (ex: Throwable) {
        // DO NOT THROW users expect we have fully handled/discarded the exception
        Exceptions.report(ex, "exceptionReporter", "Uncaught Exception")
    }
}

/** This wraps (and discards) exceptions, but it does output a log message */
fun ignoreException(silent: Boolean = false, inner: () -> Unit) {
    try {
        inner()
    } catch (ex: Throwable) {
        // DO NOT THROW users expect we have fully handled/discarded the exception
        if (!silent) Logger.e(ex) { "ignoring exception" }
    }
}

// / Convert any exceptions in this service call into a RemoteException that the client can
// / then handle
fun <T> toRemoteExceptions(inner: () -> T): T = try {
    inner()
} catch (ex: Throwable) {
    Log.e("toRemoteExceptions", "Uncaught exception, returning to remote client", ex)
    when (ex) { // don't double wrap remote exceptions
        is RemoteException -> throw ex
        else -> throw RemoteException(ex.message)
    }
}
