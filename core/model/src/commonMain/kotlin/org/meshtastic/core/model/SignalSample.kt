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
package org.meshtastic.core.model

/** One LoRa reception by the connected radio, tagged with where the phone was when it arrived. */
data class SignalSample(
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val fromNum: Int,
    val rssi: Int,
    val snr: Float?,
    /** False when a relay sent the copy that was heard, so [rssi] is the relay's signal and not [fromNum]'s. */
    val direct: Boolean,
)

/** One run of the signal survey, from turning recording on to turning it off. */
data class SignalSurveySession(
    val id: Long,
    val startedAtMillis: Long,
    /** Null while the session is recording, and for one cut short when the app shut down. */
    val endedAtMillis: Long?,
    /**
     * Name of the modem preset the radio was on, which is what an SNR reading is judged against. Null when the radio
     * used custom LoRa settings.
     */
    val modemPreset: String?,
    val readingCount: Int,
)
