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

package com.geeksville.mesh.android

import com.geeksville.mesh.util.Exceptions
import timber.log.Timber

interface Logging {

    private fun tag(): String = this.javaClass.name

    fun info(msg: String) = Timber.tag(tag()).i(msg)

    fun debug(msg: String) = Timber.tag(tag()).d(msg)

    fun warn(msg: String) = Timber.tag(tag()).w(msg)

    /**
     * Log an error message, note - we call this errormsg rather than error because error() is a stdlib function in
     * kotlin in the global namespace and we don't want users to accidentally call that.
     */
    fun errormsg(msg: String, ex: Throwable? = null) {
        if (ex?.message != null) {
            Timber.tag(tag()).e(ex, msg)
        } else {
            Timber.tag(tag()).e(msg)
        }
    }

    // / Kotlin assertions are disabled on android, so instead we use this assert helper
    fun logAssert(f: Boolean) {
        if (!f) {
            val ex = AssertionError("Assertion failed")

            // if(!Debug.isDebuggerConnected())
            throw ex
        }
    }

    // / Report an error (including messaging our crash reporter service if allowed
    fun reportError(s: String) {
        Exceptions.report(Exception("logging reportError: $s"), s)
    }
}
