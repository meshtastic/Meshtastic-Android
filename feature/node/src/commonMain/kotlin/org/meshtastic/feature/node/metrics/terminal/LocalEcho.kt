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
package org.meshtastic.feature.node.metrics.terminal

/** Floor on how long a prediction may wait for its echo, before a round trip has been measured. */
internal const val PREDICTION_TIMEOUT_MIN_MS = 4_000L
internal const val PREDICTION_TIMEOUT_MAX_MS = 20_000L
private const val PREDICTION_TIMEOUT_RTT_MULTIPLIER = 3

/** Prompts whose input the remote does not echo, so nothing typed into them may be drawn locally. */
private val SECRET_PROMPT = Regex("""(password|passphrase|passcode|\bpin\b)[^:]*:\s*$""", RegexOption.IGNORE_CASE)

/** Whether the text before the cursor reads as a prompt for a secret, such as sudo's. */
internal fun looksLikeSecretPrompt(lineBeforeCursor: String): Boolean = SECRET_PROMPT.containsMatchIn(lineBeforeCursor)

/**
 * Predictive local echo in the manner of mosh: characters already sent are drawn at once, marked as unconfirmed, and
 * retired as the remote echo arrives.
 *
 * Nothing is drawn on a line until the remote has echoed one of its characters, mosh's own rule: a prompt that does not
 * echo, such as a password, never confirms one, so what is typed into it is never shown. Once confirmed, predictions
 * are checked one character at a time against what the PTY prints. A different character - a completion, a redraw, a
 * program reading keys itself - means the guess was wrong, so they are dropped rather than corrected and the line must
 * confirm again; cursor movement and erases are how a shell echoes a backspace, so those pass. A prediction that waits
 * longer than [timeoutMs] is dropped too, and prediction stays off until the next Enter. Only the line being typed is
 * predicted; after Enter the next prompt is unknown.
 *
 * Not thread-safe, and owns no clock.
 */
internal class LocalEcho {
    private val predicted = StringBuilder()
    private var oldestSentMs = 0L
    private var suppressed = false
    private var echoConfirmed = false

    /** Whether typing on this line may be drawn before the remote echoes it. */
    val showsTyping: Boolean
        get() = echoConfirmed && !suppressed

    /** Characters sent but not yet echoed, to draw after the cursor; empty until the line has confirmed an echo. */
    val pending: String
        get() = if (showsTyping) predicted.toString() else ""

    fun onSent(text: String, nowMs: Long) {
        for (c in text) {
            when {
                c == '\r' || c == '\n' -> {
                    predicted.clear()
                    suppressed = false
                    echoConfirmed = false
                }

                c == '\u007f' || c == '\b' -> if (predicted.isNotEmpty()) predicted.deleteAt(predicted.lastIndex)

                c.isISOControl() -> {
                    predicted.clear()
                    echoConfirmed = false
                }

                !suppressed -> {
                    if (predicted.isEmpty()) oldestSentMs = nowMs
                    predicted.append(c)
                }
            }
        }
    }

    fun onOutput(events: List<EchoEvent>, nowMs: Long) {
        for (event in events) {
            if (predicted.isEmpty()) return
            when (event) {
                is EchoEvent.Printed ->
                    if (event.char == predicted[0]) {
                        predicted.deleteAt(0)
                        echoConfirmed = true
                        // The echo is flowing, so the rest of the line gets a fresh wait.
                        oldestSentMs = nowMs
                    } else {
                        predicted.clear()
                        echoConfirmed = false
                    }

                // A backspace or CR is how a shell echoes an erase; anything else here is the shell rewriting the line.
                EchoEvent.Edited -> Unit
            }
        }
    }

    /** Drops predictions that have waited too long, and keeps prediction off for the rest of the line. */
    fun tick(nowMs: Long, roundTripMs: Long?) {
        if (predicted.isEmpty()) return
        if (nowMs - oldestSentMs >= timeoutMs(roundTripMs)) {
            predicted.clear()
            suppressed = true
            echoConfirmed = false
        }
    }

    private fun timeoutMs(roundTripMs: Long?): Long =
        ((roundTripMs ?: 0L) * PREDICTION_TIMEOUT_RTT_MULTIPLIER).coerceIn(
            PREDICTION_TIMEOUT_MIN_MS,
            PREDICTION_TIMEOUT_MAX_MS,
        )
}
