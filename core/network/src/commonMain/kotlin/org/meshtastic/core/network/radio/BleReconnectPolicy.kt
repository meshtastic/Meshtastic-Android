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
package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Encapsulates the BLE reconnection policy with exponential backoff.
 *
 * The policy tracks consecutive failures and decides whether to retry or signal a transient disconnect (DeviceSleep).
 * When [maxFailures] is reached the [execute] loop invokes [execute]'s `onPermanentDisconnect` callback and returns;
 * set [maxFailures] to [Int.MAX_VALUE] (as [BleRadioTransport] does) to disable the give-up path entirely.
 *
 * @param maxFailures maximum consecutive failures before giving up; use [Int.MAX_VALUE] to retry indefinitely
 * @param failureThreshold after this many consecutive failures, signal a transient disconnect
 * @param settleDelay delay before each connection attempt to let the BLE stack settle
 * @param minStableConnection minimum time a connection must stay up to be considered "stable"
 * @param backoffStrategy computes the backoff delay for a given failure count
 */
class BleReconnectPolicy(
    private val maxFailures: Int = DEFAULT_MAX_FAILURES,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val settleDelay: Duration = DEFAULT_SETTLE_DELAY,
    /** Minimum time a connection must stay up to be considered "stable". Exposed for callers to compare uptime. */
    val minStableConnection: Duration = DEFAULT_MIN_STABLE_CONNECTION,
    private val backoffStrategy: (attempt: Int) -> Duration = ::computeReconnectBackoff,
) {
    /** Outcome of a single reconnect iteration. */
    sealed interface Outcome {
        /** Connection attempt succeeded and then eventually disconnected. */
        data class Disconnected(val wasStable: Boolean, val wasIntentional: Boolean) : Outcome

        /** Connection attempt failed with an exception. */
        data class Failed(val error: Throwable) : Outcome
    }

    /** Action the caller should take after the policy processes an outcome. */
    sealed interface Action {
        /** Retry the connection after the specified backoff delay. */
        data class Retry(val backoff: Duration) : Action

        /** Signal a transient disconnect to higher layers. */
        data class SignalTransient(val backoff: Duration) : Action

        /** Give up permanently. */
        data object GiveUp : Action

        /** Continue immediately (e.g. after an intentional disconnect). */
        data object Continue : Action
    }

    internal var consecutiveFailures: Int = 0
        private set

    /** Processes the outcome of a connection attempt and returns the action the caller should take. */
    fun processOutcome(outcome: Outcome): Action = when (outcome) {
        is Outcome.Disconnected -> {
            if (outcome.wasIntentional) {
                consecutiveFailures = 0
                Action.Continue
            } else if (outcome.wasStable) {
                consecutiveFailures = 0
                Action.Continue
            } else {
                consecutiveFailures++
                Logger.w { "Unstable connection (consecutive failures: $consecutiveFailures)" }
                evaluateFailure()
            }
        }
        is Outcome.Failed -> {
            consecutiveFailures++
            Logger.w { "Connection failed (consecutive failures: $consecutiveFailures)" }
            evaluateFailure()
        }
    }

    private fun evaluateFailure(): Action {
        if (consecutiveFailures >= maxFailures) {
            return Action.GiveUp
        }
        val backoff = backoffStrategy(consecutiveFailures)
        return if (consecutiveFailures >= failureThreshold) {
            Action.SignalTransient(backoff)
        } else {
            Action.Retry(backoff)
        }
    }

    /**
     * Runs the reconnect loop, calling [attempt] for each iteration.
     *
     * The [attempt] lambda should perform a single connect-and-wait cycle and return the [Outcome] when the connection
     * drops or an error occurs.
     *
     * @param attempt performs a single connection attempt and returns the outcome
     * @param onTransientDisconnect called when the policy decides to signal a transient disconnect
     * @param onPermanentDisconnect called when the policy gives up permanently
     */
    suspend fun execute(
        attempt: suspend () -> Outcome,
        onTransientDisconnect: suspend (Throwable?) -> Unit,
        onPermanentDisconnect: suspend (Throwable?) -> Unit,
    ) {
        while (coroutineContext.isActive) {
            delay(settleDelay)

            val outcome = attempt()
            val lastError = (outcome as? Outcome.Failed)?.error

            when (val action = processOutcome(outcome)) {
                is Action.Continue -> continue
                is Action.Retry -> {
                    Logger.d { "Retrying in ${action.backoff} (failure #$consecutiveFailures)" }
                    delay(action.backoff)
                }
                is Action.SignalTransient -> {
                    onTransientDisconnect(lastError)
                    Logger.d { "Retrying in ${action.backoff} (failure #$consecutiveFailures)" }
                    delay(action.backoff)
                }
                is Action.GiveUp -> {
                    Logger.e { "Giving up after $consecutiveFailures consecutive failures" }
                    onPermanentDisconnect(lastError)
                    return
                }
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_FAILURES = 10
        const val DEFAULT_FAILURE_THRESHOLD = 3

        /**
         * Delay applied before every connection attempt (including the first) so the BLE stack and the firmware-side
         * GATT session have time to settle.
         *
         * Empirically validated against the meshtastic-client KMP SDK probes (Apr 2026): with a 1.5 s pause between
         * disconnect→reconnect cycles, 3/5–4/5 attempts failed mid-handshake (Stage1Draining timeouts) because the
         * firmware had not yet released its GATT session from the previous cycle. With ≥ 5 s pause, success rate rose
         * to 5/5 against a strong (-53 dBm) link. 3 s is a conservative compromise on Android, whose BLE stack is more
         * mature than btleplug+CoreBluetooth, but the firmware-side cleanup constraint is the same.
         */
        val DEFAULT_SETTLE_DELAY = 3.seconds
        val DEFAULT_MIN_STABLE_CONNECTION = 5.seconds

        internal val RECONNECT_BASE_DELAY = 5.seconds
        internal val RECONNECT_MAX_DELAY = 60.seconds
        internal const val BACKOFF_MAX_EXPONENT = 4
    }
}

/**
 * Returns the reconnect backoff delay for a given consecutive failure count.
 *
 * Backoff schedule: 1 failure → 5 s, 2 failures → 10 s, 3 failures → 20 s, 4 failures → 40 s, 5+ failures → 60 s
 * (capped).
 */
internal fun computeReconnectBackoff(consecutiveFailures: Int): Duration {
    if (consecutiveFailures <= 0) return BleReconnectPolicy.RECONNECT_BASE_DELAY
    val multiplier = 1 shl (consecutiveFailures - 1).coerceAtMost(BleReconnectPolicy.BACKOFF_MAX_EXPONENT)
    return minOf(BleReconnectPolicy.RECONNECT_BASE_DELAY * multiplier, BleReconnectPolicy.RECONNECT_MAX_DELAY)
}
