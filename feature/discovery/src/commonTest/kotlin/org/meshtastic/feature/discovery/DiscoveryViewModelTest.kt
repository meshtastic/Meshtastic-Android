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
package org.meshtastic.feature.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.MeshBeacon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers design#140 behavior 10's presentation-time filtering exactly as [DiscoveryViewModel] wires it: these tests
 * call the same [filterAlreadyJoinedOffers]/[filterAlreadyJoinedBeaconChannels] functions the view model's
 * `beaconOffers`/`beaconChannels` combine over, so the wiring itself is exercised without constructing the full view
 * model and its dependency graph (matcher edge cases already have direct coverage in `MeshBeaconOfferTest`).
 */
class DiscoveryViewModelTest {

    private val radioLora = LoRaConfig(use_preset = true, modem_preset = ModemPreset.LONG_FAST)
    private val partyNetOffer =
        MeshBeaconOffer(
            fromNodeNum = 456,
            beacon =
            MeshBeacon(
                message = "Join us",
                offer_channel = ChannelSettings(name = "PartyNet"),
                offer_preset = ModemPreset.LONG_FAST,
            ),
        )

    @Test
    fun `offer for an unconfigured channel passes through both filters`() {
        val channels = listOf(ChannelSettings(name = "HomeMesh"))

        val offers = filterAlreadyJoinedOffers(listOf(partyNetOffer), radioLora, channels)
        val beaconChannels = filterAlreadyJoinedBeaconChannels(listOf(partyNetOffer), radioLora, channels)

        assertEquals(listOf(partyNetOffer), offers)
        assertEquals(listOf("PartyNet"), beaconChannels.map { it.name })
    }

    @Test
    fun `offer matching a configured channel is dropped from both filters`() {
        val channels = listOf(ChannelSettings(name = "PartyNet"))

        val offers = filterAlreadyJoinedOffers(listOf(partyNetOffer), radioLora, channels)
        val beaconChannels = filterAlreadyJoinedBeaconChannels(listOf(partyNetOffer), radioLora, channels)

        assertTrue(offers.isEmpty(), "an already-joined offer must not appear in the invitations list")
        assertTrue(beaconChannels.isEmpty(), "an already-joined channel must not appear as a scan target")
    }

    @Test
    fun `filtering never mutates the offers list it is given`() {
        // The repository itself must never be touched by presentation-time filtering (spec: reactive, not destructive).
        val original = listOf(partyNetOffer)
        filterAlreadyJoinedOffers(original, LoRaConfig(use_preset = true), listOf(ChannelSettings(name = "PartyNet")))
        assertEquals(1, original.size)
    }

    @Test
    fun `beacon channels filter still deduplicates by id once already-joined entries are removed`() {
        val duplicate = partyNetOffer.copy(fromNodeNum = 789) // Same channel from a second node — one row, not two.
        val beaconChannels =
            filterAlreadyJoinedBeaconChannels(
                listOf(partyNetOffer, duplicate),
                radioLora,
                listOf(ChannelSettings(name = "HomeMesh")),
            )
        assertEquals(1, beaconChannels.size)
    }

    @Test
    fun `reactively combining offers with a later-changing channel set brings a filtered invitation back`() = runTest {
        // Mirrors DiscoveryViewModel's own combine(offers, currentLora, currentChannels) wiring: proves the filter is
        // reactive at presentation time, not a one-shot decision baked in when the offer arrived.
        val offersFlow = MutableStateFlow(listOf(partyNetOffer))
        val loraFlow = MutableStateFlow<LoRaConfig?>(radioLora)
        val channelsFlow = MutableStateFlow(listOf(ChannelSettings(name = "PartyNet")))

        val results = mutableListOf<List<MeshBeaconOffer>>()
        val job = launch {
            combine(offersFlow, loraFlow, channelsFlow, ::filterAlreadyJoinedOffers).collect { results += it }
        }
        runCurrent()

        assertTrue(results.last().isEmpty(), "already configured, so the invitation starts out hidden")

        // The user deletes the "PartyNet" channel — the still-stored offer must reappear without dismiss()/repository
        // mutation, since MeshBeaconRepository is never touched by this filter.
        channelsFlow.value = emptyList()
        runCurrent()
        assertEquals(listOf(partyNetOffer), results.last())

        // The user re-adds an unrelated channel — still not a match, offer stays visible.
        channelsFlow.value = listOf(ChannelSettings(name = "HomeMesh"))
        runCurrent()
        assertEquals(listOf(partyNetOffer), results.last())

        job.cancel()
    }
}
