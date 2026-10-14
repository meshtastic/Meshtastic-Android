/*
 * GABAGOOL RADIO additions, GPL-3.0-or-later.
 * Reconnection policy only: callers own transport lifecycle and user consent.
 */
package org.meshtastic.core.ble

/**
 * Bounded exponential reconnect delay, shared by Android and Desktop transports.
 *
 * [failureCount] starts at 1 for the first failed attempt. A negative or zero
 * count is treated as the first attempt. No connection is initiated here.
 */
internal object GabagoolReconnectPolicy {
    private const val INITIAL_DELAY_MS = 1_000L
    private const val MAX_DELAY_MS = 60_000L

    fun delayMillis(failureCount: Int): Long {
        val shifts = (failureCount - 1).coerceIn(0, 6)
        return (INITIAL_DELAY_MS shl shifts).coerceAtMost(MAX_DELAY_MS)
    }

    /** Never reconnect if the user explicitly disconnected or the app is shutting down. */
    fun shouldRetry(userDisconnected: Boolean, shuttingDown: Boolean): Boolean =
        !userDisconnected && !shuttingDown
}
