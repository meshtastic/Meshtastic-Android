package com.geeksville.mesh.android

import android.os.Build
import android.util.Log
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.util.Exceptions

/**
 * Created by kevinh on 12/24/14.
 */

typealias LogPrinter = (Int, String, String) -> Unit

interface Logging {

    companion object {
        /** Some vendors strip log messages unless the severity is super high.
         *
         * alps == Soyes
         * HMD Global == mfg of the Nokia 7.2
         */
        private val badVendors = setOf("OnePlus", "alps", "HMD Global", "Sony")

        /// if false NO logs will be shown, set this in the application based on BuildConfig.DEBUG
        var showLogs = true

        /** if true, all logs will be printed at error level.  Sometimes necessary for buggy ROMs
         * that filter logcat output below this level.
         *
         * Since there are so many bad vendors, we just always lie if we are a release build
         */
        var forceErrorLevel = !BuildConfig.DEBUG || badVendors.contains(Build.MANUFACTURER)

        /// If false debug logs will not be shown (but others might)
        var showDebug = true

        /**
         * By default all logs are printed using the standard android Log class.  But clients
         * can change printlog to a different implementation (for logging to files or via
         * google crashlytics)
         */
        var printlog: LogPrinter = { level, tag, message ->
            if (showLogs) {
                if (showDebug || level > Log.DEBUG) {
                    Log.println(if (forceErrorLevel) Log.ERROR else level, tag, message)
                }
            }
        }
    }

    private fun tag(): String = this.javaClass.getName()

    fun info(msg: String) = printlog(Log.INFO, tag(), msg)
    fun verbose(msg: String) = printlog(Log.VERBOSE, tag(), msg)
    fun debug(msg: String) = printlog(Log.DEBUG, tag(), msg)
    fun warn(msg: String) = printlog(Log.WARN, tag(), msg)

    /**
     * Log an error message, note - we call this errormsg rather than error because error() is
     * a stdlib function in kotlin in the global namespace and we don't want users to accidentally call that.
     */
    fun errormsg(msg: String, ex: Throwable? = null) {
        if (ex?.message != null)
            printlog(Log.ERROR, tag(), "$msg (exception ${ex.message})")
        else
            printlog(Log.ERROR, tag(), "$msg")
    }

    /// Kotlin assertions are disabled on android, so instead we use this assert helper
    fun logAssert(f: Boolean) {
        if (!f) {
            val ex = AssertionError("Assertion failed")

            // if(!Debug.isDebuggerConnected())
            throw ex
        }
    }

    /// Report an error (including messaging our crash reporter service if allowed
    fun reportError(s: String) {
        Exceptions.report(Exception("logging reportError: $s"), s)
    }
}