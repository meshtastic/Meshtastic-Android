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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.isTcp
import org.meshtastic.proto.Position

/** What a survey needs to know about the connected radio. */
@Single
class SignalSurveyRadio(
    private val radioPrefs: RadioPrefs,
    private val radioConfigRepository: RadioConfigRepository,
    nodeRepository: NodeRepository,
) {
    /** Reached over the network, so it may be anywhere relative to the phone. */
    val isRemote: Boolean
        get() = radioPrefs.isTcp()

    /** The radio's position as it last reported its own, or null while it has reported none of its own. */
    internal val ownFix: Flow<RadioFix?> =
        nodeRepository.ourNodeInfo.map { it?.position?.toRadioFix() }.distinctUntilChanged()

    suspend fun modemPresetName(): String? {
        val lora = radioConfigRepository.localConfigFlow.firstOrNull()?.lora
        return lora?.takeIf { it.use_preset }?.modem_preset?.name
    }
}

/** A position the radio reported for itself, from its own GPS or a position set on it. */
internal data class RadioFix(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    /** A fixed position, or a GPS reporting no ground speed: where the radio is until it says otherwise. */
    val still: Boolean,
    val fromGps: Boolean,
)

private const val DEGREES_PER_E7 = 1e-7
private const val STILL_KMPH = 2
private const val MS_PER_SECOND = 1000L

/**
 * Null for a position the phone gave the radio, which would only echo the phone's own fix back late. Firmware sends
 * `ground_speed` in km/h.
 */
internal fun Position.toRadioFix(): RadioFix? {
    // Firmware never sends a fix at exactly 0,0; it means no position.
    val lat = latitude_i ?: 0
    val lon = longitude_i ?: 0
    val fixed = location_source == Position.LocSource.LOC_MANUAL
    val gps = location_source == Position.LocSource.LOC_INTERNAL
    val seconds = timestamp.takeIf { it > 0 } ?: time
    return if ((lat == 0 && lon == 0) || !(fixed || gps)) {
        null
    } else {
        RadioFix(
            latitude = lat * DEGREES_PER_E7,
            longitude = lon * DEGREES_PER_E7,
            timeMillis = seconds * MS_PER_SECOND,
            still = fixed || ground_speed?.let { it <= STILL_KMPH } == true,
            fromGps = gps,
        )
    }
}
