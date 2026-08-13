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
package org.meshtastic.core.ui.component

import org.meshtastic.core.model.Node

/**
 * Current-radio observations for a retained [Node].
 *
 * The phone's node database is intentionally cumulative, while one completed radio NodeDB download can be a strict
 * subset of it. A node absent from that completed download still has useful identity and history, but its cached
 * receiver-relative fields must not be presented as observations from the current connection.
 */
internal data class CurrentRadioNodePresentation(
    val isSavedOnPhoneOnly: Boolean,
    val isOnline: Boolean,
    val lastHeard: Int?,
    val hopsAway: Int?,
    val snr: Float?,
    val rssi: Int?,
)

/**
 * Projects cached node data for the current connection.
 *
 * [isInCurrentRadioNodeSnapshot] is tri-state: `false` is proof from a completed current-session snapshot that the node
 * is retained only on the phone; `true` is positive membership; `null` means no completed snapshot is available.
 * Unknown state deliberately preserves the existing presentation. Numeric zero is never used as absence: 0 hops, 0 dB
 * SNR, and 0 dBm RSSI remain genuine values when membership is true or unknown.
 */
internal fun Node.currentRadioPresentation(isInCurrentRadioNodeSnapshot: Boolean?): CurrentRadioNodePresentation {
    val isSavedOnPhoneOnly = isInCurrentRadioNodeSnapshot == false
    return CurrentRadioNodePresentation(
        isSavedOnPhoneOnly = isSavedOnPhoneOnly,
        isOnline = !isSavedOnPhoneOnly && isOnline,
        lastHeard = lastHeard.takeUnless { isSavedOnPhoneOnly },
        hopsAway = hopsAway.takeUnless { isSavedOnPhoneOnly },
        snr = snrOrNull.takeUnless { isSavedOnPhoneOnly },
        rssi = rssiOrNull.takeUnless { isSavedOnPhoneOnly },
    )
}
