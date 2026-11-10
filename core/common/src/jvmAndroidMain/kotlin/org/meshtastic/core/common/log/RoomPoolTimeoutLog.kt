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
package org.meshtastic.core.common.log

import co.touchlab.kermit.Logger
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Room's connection pool reports an acquire timeout, with a dump naming each connection's holder, only through
 * `printStackTrace` on `System.err`, which reaches neither Kermit nor the crash backends. This forwards that one
 * throwable's message to the log, at most once per interval, and passes all output through unchanged.
 */
object RoomPoolTimeoutLog {
    private const val MIN_INTERVAL_NANOS = 60_000_000_000L
    private const val MAX_FORWARDED_CHARS = 4_000

    /** Wraps `System.err` once; a second call is a no-op. */
    fun install() {
        val current = System.err
        if (current !is Tap) System.setErr(Tap(current, MIN_INTERVAL_NANOS) { System.nanoTime() })
    }

    /**
     * Matched on the message alone: androidx.sqlite prefixes it with `Error code: 5, message: `, and R8 renames
     * `SQLiteException` in release builds.
     */
    internal fun isRoomPoolTimeout(throwable: Throwable): Boolean =
        throwable.message.orEmpty().contains("Timed out attempting to acquire a ")

    /** Drops the dump's stack frames, which are Room and dispatcher internals; the holders' contexts name them. */
    internal fun summarize(message: String): String = message
        .lineSequence()
        .filterNot { it.trimStart().startsWith("at ") }
        .joinToString("\n")
        .take(MAX_FORWARDED_CHARS)

    /**
     * `Throwable.printStackTrace` prints the throwable itself, message and dump included, through `println(Any)`. No
     * auto-flush: Android's stderr logs a partial line on every flush, so flushing each write would split one line into
     * several logcat entries. The delegate keeps its own flushing.
     */
    internal class Tap(delegate: PrintStream, private val minIntervalNanos: Long, private val nowNanos: () -> Long) :
        PrintStream(delegate, false) {
        private val lastForward = AtomicLong(Long.MIN_VALUE)

        override fun println(x: Any?) {
            if (x is Throwable && isRoomPoolTimeout(x)) forward(x)
            super.println(x)
        }

        private fun forward(timeout: Throwable) {
            val now = nowNanos()
            val last = lastForward.get()
            if (last != Long.MIN_VALUE && now - last < minIntervalNanos) return
            if (!lastForward.compareAndSet(last, now)) return
            Logger.withTag("RoomPool").w { summarize(timeout.message.orEmpty()) }
        }
    }
}
