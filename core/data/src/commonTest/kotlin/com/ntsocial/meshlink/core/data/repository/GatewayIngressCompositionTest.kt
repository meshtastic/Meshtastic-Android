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
package com.ntsocial.meshlink.core.data.repository

import com.ntsocial.meshlink.core.common.database.DatabaseManager
import com.ntsocial.meshlink.core.data.di.CoreDataModule
import com.ntsocial.meshlink.core.data.di.module
import com.ntsocial.meshlink.core.data.manager.RadioIngressWorkTracker
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialGatewayHistoryState
import com.ntsocial.meshlink.core.repository.CommandSender
import com.ntsocial.meshlink.core.repository.GatewayIngressSessionGate
import com.ntsocial.meshlink.core.repository.MessageQueue
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.PacketRepository
import com.ntsocial.meshlink.core.repository.RadioConfigRepository
import com.ntsocial.meshlink.core.repository.RadioInterfaceService
import com.ntsocial.meshlink.core.testing.FakeDatabaseManager
import com.ntsocial.meshlink.core.testing.FakeNodeRepository
import com.ntsocial.meshlink.core.testing.FakeRadioConfigRepository
import com.ntsocial.meshlink.core.testing.FakeRadioInterfaceService
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertTrue

class GatewayIngressCompositionTest {
    @Test
    fun `generated production repository opens the gate resolved by the queue and packet handler`() = runTest {
        val address = "xAA:BB:CC:DD:EE:01"
        val radio =
            FakeRadioInterfaceService(backgroundScope).apply {
                setDeviceAddress(address)
                onConnect()
                markCurrentSessionConfigured(radioSessionState.value.epoch)
            }
        val channels =
            FakeRadioConfigRepository().apply {
                setCompleteChannelReadback(ChannelSet(settings = listOf(ChannelSettings(name = "NTsocial"))))
            }
        val packets = mock<PacketRepository>(MockMode.autofill)
        val history = NtsocialGatewayHistoryState("composition-epoch", 0)
        every { packets.getGatewayHistoryState(emptyList()) } returns MutableStateFlow(history)
        everySuspend { packets.readCurrentGatewayHistoryState(emptyList()) } returns history
        val sharedGate = GatewayIngressSessionGate()
        val app = koinApplication {
            modules(
                CoreDataModule().module(),
                module {
                    single<CommandSender> { mock(MockMode.autofill) }
                    single<PacketRepository> { packets }
                    single<MessageQueue> { mock(MockMode.autofill) }
                    single<NodeRepository> { FakeNodeRepository() }
                    single<RadioConfigRepository> { channels }
                    single<RadioInterfaceService> { radio }
                    single<DatabaseManager> { FakeDatabaseManager().apply { setCurrentAddressForTest(address) } }
                    single { RadioIngressWorkTracker() }
                    single<CoroutineScope>(named("ServiceScope")) { backgroundScope }
                    single { sharedGate }
                },
            )
        }
        try {
            val repository = app.koin.get<NtsocialGatewayRepository>()
            val epoch = radio.radioSessionState.value.epoch
            assertTrue(repository.activateInboundSession(epoch))
            assertTrue(repository.isInboundSessionActive(epoch))
            assertTrue(
                app.koin.get<GatewayIngressSessionGate>().isActive(epoch),
                "Production DI must publish the same gate consumed by durable dispatch",
            )
        } finally {
            app.close()
        }
    }
}
