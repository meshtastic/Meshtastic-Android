/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeMeshtasticRadioProfile : MeshtasticRadioProfile {
    private val _fromRadio = MutableSharedFlow<ByteArray>(replay = 1)
    override val fromRadio: Flow<ByteArray> = _fromRadio

    private val _logRadio = MutableSharedFlow<ByteArray>(replay = 1)
    override val logRadio: Flow<ByteArray> = _logRadio

    val sentPackets = mutableListOf<ByteArray>()

    override suspend fun sendToRadio(packet: ByteArray) {
        sentPackets.add(packet)
    }

    suspend fun emitFromRadio(packet: ByteArray) {
        _fromRadio.emit(packet)
    }

    suspend fun emitLogRadio(packet: ByteArray) {
        _logRadio.emit(packet)
    }
}

class MeshtasticRadioProfileTest {

    @Test
    fun testFakeProfileEmitsFromRadio() = runTest {
        val fake = FakeMeshtasticRadioProfile()
        val expectedPacket = byteArrayOf(1, 2, 3)

        fake.emitFromRadio(expectedPacket)

        // This test should fail initially because we haven't implemented the real abstraction
        // Actually, it will fail to compile because MeshtasticRadioProfile doesn't exist in core:ble.
        val received = fake.fromRadio.first()
        assertEquals(expectedPacket.toList(), received.toList())
    }

    @Test
    fun testFakeProfileRecordsSentPackets() = runTest {
        val fake = FakeMeshtasticRadioProfile()
        val packet = byteArrayOf(4, 5, 6)

        fake.sendToRadio(packet)

        assertEquals(1, fake.sentPackets.size)
        assertEquals(packet.toList(), fake.sentPackets.first().toList())
    }
}
