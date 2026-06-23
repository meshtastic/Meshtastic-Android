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
package org.meshtastic.feature.firmware.ota

import kotlinx.coroutines.delay
import org.meshtastic.core.common.util.NumberFormatter

private const val PERCENT_MAX = 100
private const val KIB_DIVISOR = 1024f

/**
 * Formats firmware-transfer progress as a human-readable detail string, e.g. `"42% (12.3 KiB/s, ETA: 5s)"`.
 *
 * When [bytesPerSecond] is non-positive (no throughput sample yet) only the percentage is returned — no empty
 * parentheses. Shared by the ESP32 OTA and Nordic DFU update handlers, which differ only in how they obtain the inputs.
 */
internal fun formatTransferProgress(progress: Float, totalBytes: Int, bytesPerSecond: Long): String {
    val percent = (progress * PERCENT_MAX).toInt()
    if (bytesPerSecond <= 0L) return "$percent%"
    val kibPerSecond = bytesPerSecond.toFloat() / KIB_DIVISOR
    val bytesSent = (progress * totalBytes).toLong()
    val etaSeconds = ((totalBytes - bytesSent).toFloat() / bytesPerSecond).toInt()
    return "$percent% (${NumberFormatter.format(kibPerSecond, 1)} KiB/s, ETA: ${etaSeconds}s)"
}

/**
 * Runs [block] up to [attempts] times, returning the first successful [Result]. [onAttempt] fires before each attempt
 * (1-based) for progress reporting, and [retryDelayMillis] is waited between tries (never after the last). If every
 * attempt fails, returns the last failure so the caller can surface it however it likes (rethrow as-is vs. wrap in a
 * domain exception).
 */
internal suspend fun <T> retryWithDelay(
    attempts: Int,
    retryDelayMillis: Long,
    onAttempt: (attempt: Int) -> Unit,
    block: suspend (attempt: Int) -> Result<T>,
): Result<T> {
    var lastError: Throwable? = null
    for (attempt in 1..attempts) {
        onAttempt(attempt)
        val result = block(attempt)
        if (result.isSuccess) return result
        lastError = result.exceptionOrNull()
        if (attempt < attempts) delay(retryDelayMillis)
    }
    return Result.failure(lastError ?: IllegalStateException("retryWithDelay: all $attempts attempts failed"))
}
