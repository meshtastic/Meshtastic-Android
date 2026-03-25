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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ExportDataUseCaseTest {

    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var meshLogRepository: FakeMeshLogRepository
    private lateinit var useCase: ExportDataUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        meshLogRepository = FakeMeshLogRepository()
        useCase = ExportDataUseCase(nodeRepository, meshLogRepository)
    }

    @Test
    fun `invoke writes header to sink`() = runTest {
        val buffer = Buffer()
        useCase(buffer, 1)

        val output = buffer.readUtf8()
        assertTrue(output.startsWith("\"date\",\"time\",\"from\""))
    }

    @Test
    fun `invoke writes packet data to sink`() = runTest {
        val buffer = Buffer()
        val log = MeshLog(
            uuid = "1",
            message_type = "TEXT",
            received_date = 1000000000L,
            raw_message = "",
            fromRadio = FromRadio(
                packet = MeshPacket(
                    from = 1234,
                    rx_snr = 5.0f,
                    decoded = Data(
                        portnum = PortNum.TEXT_MESSAGE_APP,
                        payload = "Hello".encodeUtf8()
                    )
                )
            )
        )
        meshLogRepository.setLogs(listOf(log))

        useCase(buffer, 1)

        val output = buffer.readUtf8()
        assertTrue(output.contains("\"1234\""))
        assertTrue(output.contains("Hello"))
    }
}
