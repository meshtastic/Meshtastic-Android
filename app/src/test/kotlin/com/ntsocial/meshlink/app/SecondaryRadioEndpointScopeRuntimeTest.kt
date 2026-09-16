/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ntsocial.meshlink.app.radio.RadioEndpointScopeRegistry
import com.ntsocial.meshlink.core.data.manager.MeshConnectionManagerImpl
import com.ntsocial.meshlink.core.data.repository.NtsocialGatewayRepositoryImpl
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointId
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointProfile
import com.ntsocial.meshlink.core.radiofleet.RadioEndpointSessionFactory
import com.ntsocial.meshlink.core.radiofleet.RadioProtocol
import com.ntsocial.meshlink.core.repository.MeshConnectionManager
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecondaryRadioEndpointScopeRuntimeTest {
    @Test
    fun `secondary scope resolves the complete connection graph and owns an isolated gateway repository`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        AndroidKoinBootstrap.ensureStarted(application)
        val koin = GlobalContext.get()
        val endpointId = RadioEndpointId("secondary-runtime-graph")
        val session =
            koin
                .get<RadioEndpointSessionFactory>()
                .create(
                    RadioEndpointProfile(
                        id = endpointId,
                        protocol = RadioProtocol.MESHTASTIC,
                        transportAddress = "xAA:BB:CC:DD:EE:02",
                        displayName = "Secondary",
                    ),
                )

        try {
            val endpointScope = assertNotNull(koin.get<RadioEndpointScopeRegistry>().get(endpointId))
            assertIs<MeshConnectionManagerImpl>(endpointScope.get<MeshConnectionManager>())
            val secondary = assertIs<NtsocialGatewayRepositoryImpl>(endpointScope.get<NtsocialGatewayRepository>())
            kotlin.test.assertNotSame(koin.get<NtsocialGatewayRepository>(), secondary)
            val config = endpointScope.get<com.ntsocial.meshlink.core.repository.RadioConfigRepository>()
            config.replaceChannelSet(
                org.meshtastic.proto.ChannelSet(
                    settings = listOf(org.meshtastic.proto.ChannelSettings(name = "NTsocial")),
                ),
                completeReadback = true,
            )
            val raw =
                com.ntsocial.meshlink.core.model.ntsocial.NtsocialEnvelopeCodec.encode(
                    headerMsgId = okio.ByteString.of(*ByteArray(16) { 1 }),
                    payload = okio.ByteString.of(1, 2, 3),
                )
            val result =
                secondary.persistAndQueueRawEnvelope(
                    rawEnvelope = raw,
                    sourceChannelId = null,
                    to = com.ntsocial.meshlink.core.model.DataPacket.ID_BROADCAST,
                    channelIndex = 0,
                    hopLimit = 3,
                    wantAck = true,
                    packetId = 8192,
                )
            kotlin.test.assertEquals(8192, result.packetId)
            val stored =
                assertNotNull(
                    endpointScope
                        .get<com.ntsocial.meshlink.core.repository.PacketRepository>()
                        .getDurablePacketByPacketId(8192),
                )
            kotlin.test.assertEquals(raw, stored.packet.bytes)
            kotlin.test.assertTrue(stored.requiresGatewaySession)
            kotlin.test.assertEquals(result.sourceChannelId, stored.expectedSourceChannelId)
            kotlin.test.assertNull(
                koin.get<com.ntsocial.meshlink.core.repository.PacketRepository>().getPacketByPacketId(8192),
            )
            kotlin.test.assertTrue(koin.get<NtsocialGatewayRepository>().cachedEnvelopes.value.isEmpty())
            kotlin.test.assertNotSame(
                koin.get<com.ntsocial.meshlink.core.repository.PacketRepository>(),
                endpointScope.get<com.ntsocial.meshlink.core.repository.PacketRepository>(),
            )
            kotlin.test.assertNotSame(
                koin.get<com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate>(),
                endpointScope.get<com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate>(),
            )
        } finally {
            session.close()
        }
    }
}
