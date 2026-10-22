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
@file:Suppress("PreviewPublic", "MagicNumber") // public so :screenshot-tests can reference them; fixture values

package org.meshtastic.feature.settings.radio.component

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.network.mqtt.LatLon
import org.meshtastic.core.network.mqtt.MqttRootDirectory
import org.meshtastic.core.network.mqtt.MqttTopicDiscovery
import org.meshtastic.core.network.mqtt.MqttTopicSampler
import org.meshtastic.core.repository.GeocodedPlace
import org.meshtastic.core.ui.theme.AppTheme

@PreviewLightDark
@Composable
fun MqttTopicFinderFinishedPreview() {
    val state = remember { sampleState(MqttTopicSampler.Phase.Finished) }
    AppTheme { Surface { MqttTopicFinderContent(state, "mqtt.meshtastic.org", "msh/US/TX", {}, {}) } }
}

@PreviewLightDark
@Composable
fun MqttTopicFinderListeningPreview() {
    val state = remember { sampleState(MqttTopicSampler.Phase.Sampling(secondsRemaining = 37)) }
    AppTheme { Surface { MqttTopicFinderContent(state, "mqtt.meshtastic.org", "", {}, {}) } }
}

/** A Texarkana sample: its own state, two neighbors, a busy unrelated state and the capped region stream. */
private fun sampleState(phase: MqttTopicSampler.Phase): MqttTopicFinderViewModel.UiState {
    val location = LatLon(33.4418, -94.0377)
    val tally = MqttTopicDiscovery.Tally()
    fun heard(root: String, gateways: Int, channel: String = "LongFast") = repeat(gateways) {
        val gateway = (root.hashCode() + it).toUInt().toString(radix = 16).padStart(length = 8, padChar = '0')
        tally.record("$root/2/e/$channel/!$gateway", ByteArray(0))
    }
    heard("msh/US", gateways = 40)
    tally.markTruncated("msh/US")
    heard("msh/US/TX", gateways = 9)
    heard("msh/US/TX/dfw", gateways = 3)
    heard("msh/US/AR", gateways = 5)
    heard("msh/US/OK", gateways = 2, channel = "MediumFast")
    heard("msh/US/UT", gateways = 12)
    heard("msh/US/CA/socalmesh", gateways = 4)
    val activity = tally.snapshot()
    return MqttTopicFinderViewModel.UiState(
        regionName = "US",
        place = GeocodedPlace("US", "Texas", "Texarkana"),
        location = location,
        sample =
        MqttTopicSampler.State(
            phase = phase,
            activity = activity,
            messageCount = activity.values.sumOf { it.messageCount },
        ),
        directory = TEXAS_DIRECTORY,
    )
}

/** The slice of the published directory a Texarkana plan reads. */
private val TEXAS_DIRECTORY =
    MqttRootDirectory(
        entries = emptyList(),
        neighbors = mapOf("US-TX" to setOf("US-AR", "US-LA", "US-NM", "US-OK")),
        subdivisionSegments = mapOf("US" to mapOf("texas" to "TX")),
    )
