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
import org.meshtastic.core.model.Node
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(output.contains("\"hop limit\",\"hop start\",\"relay node\",\"payload\""))
    }

    @Test
    fun `invoke writes packet data to sink`() = runTest {
        val buffer = Buffer()
        val log =
            MeshLog(
                uuid = "1",
                message_type = "TEXT",
                received_date = 1000000000L,
                raw_message = "",
                fromRadio =
                FromRadio.Builder()
                    .also { wb ->
                        wb.packet =
                            MeshPacket.Builder()
                                .also { wb ->
                                    wb.from = 1234
                                    wb.rx_snr = 5.0f
                                    wb.decoded =
                                        Data.Builder()
                                            .also { wb ->
                                                wb.portnum = PortNum.TEXT_MESSAGE_APP
                                                wb.payload = "Hello".encodeUtf8()
                                            }
                                            .build()
                                }
                                .build()
                    }
                    .build(),
            )
        meshLogRepository.setLogs(listOf(log))

        useCase(buffer, 1)

        val output = buffer.readUtf8()
        assertTrue(output.contains("\"1234\""))
        assertTrue(output.contains("Hello"))
    }

    @Test
    fun `invoke names the sender by broadcast name and never the local display name`() = runTest {
        nodeRepository.setNodes(
            listOf(
                Node(
                    num = 1234,
                    user = User.Builder().also { wb -> wb.long_name = "Broadcast" }.build(),
                    displayName = "Secret",
                ),
            ),
        )
        val log =
            MeshLog(
                uuid = "1",
                message_type = "TEXT",
                received_date = 1000000000L,
                raw_message = "",
                fromRadio =
                FromRadio.Builder()
                    .also { wb ->
                        wb.packet =
                            MeshPacket.Builder()
                                .also { pb ->
                                    pb.from = 1234
                                    pb.decoded =
                                        Data.Builder()
                                            .also { db ->
                                                db.portnum = PortNum.TEXT_MESSAGE_APP
                                                db.payload = "Hello".encodeUtf8()
                                            }
                                            .build()
                                }
                                .build()
                    }
                    .build(),
            )
        meshLogRepository.setLogs(listOf(log))
        val buffer = Buffer()

        useCase(buffer, 1)

        val output = buffer.readUtf8()
        assertTrue(output.contains("Broadcast"))
        assertFalse(output.contains("Secret"))
    }

    @Test
    fun `invoke writes hop start and relay node columns`() = runTest {
        val buffer = Buffer()
        val log =
            MeshLog(
                uuid = "1",
                message_type = "TEXT",
                received_date = 1000000000L,
                raw_message = "",
                fromRadio =
                FromRadio.Builder()
                    .also { wb ->
                        wb.packet =
                            MeshPacket.Builder()
                                .also { wb ->
                                    wb.from = 1234
                                    wb.rx_snr = 5.0f
                                    wb.hop_limit = 2
                                    wb.hop_start = 3
                                    wb.relay_node = 77
                                    // 0x4d -> matches the tail of a node id such as !........4d
                                    wb.decoded =
                                        Data.Builder()
                                            .also { wb ->
                                                wb.portnum = PortNum.TEXT_MESSAGE_APP
                                                wb.payload = "Hello".encodeUtf8()
                                            }
                                            .build()
                                }
                                .build()
                    }
                    .build(),
            )
        meshLogRepository.setLogs(listOf(log))

        useCase(buffer, 1)

        val output = buffer.readUtf8()
        // hop limit, hop start, relay node (hex), payload
        assertTrue(output.contains("\"2\",\"3\",\"4d\",\"Hello\""))
    }

    @Test
    fun `invoke leaves relay node blank when unset`() = runTest {
        val buffer = Buffer()
        val log =
            MeshLog(
                uuid = "1",
                message_type = "TEXT",
                received_date = 1000000000L,
                raw_message = "",
                fromRadio =
                FromRadio.Builder()
                    .also { wb ->
                        wb.packet =
                            MeshPacket.Builder()
                                .also { wb ->
                                    wb.from = 1234
                                    wb.rx_snr = 5.0f
                                    wb.decoded =
                                        Data.Builder()
                                            .also { wb ->
                                                wb.portnum = PortNum.TEXT_MESSAGE_APP
                                                wb.payload = "Hello".encodeUtf8()
                                            }
                                            .build()
                                }
                                .build()
                    }
                    .build(),
            )
        meshLogRepository.setLogs(listOf(log))

        useCase(buffer, 1)

        val output = buffer.readUtf8()
        // relay_node defaults to 0 (unset) -> blank field before the payload
        assertTrue(output.contains("\"0\",\"\",\"Hello\""))
    }

    @Test
    fun `invoke writes one row per log in the order the repository reads them`() = runTest {
        val senders = listOf(30, 10, 20, 50, 40)
        meshLogRepository.setLogs(
            senders.mapIndexed { index, from ->
                MeshLog(
                    uuid = "$index",
                    message_type = "TEXT",
                    received_date = 1000000000L + index,
                    raw_message = "",
                    fromRadio =
                    FromRadio.Builder()
                        .also { wb ->
                            wb.packet =
                                MeshPacket.Builder()
                                    .also { wb ->
                                        wb.from = from
                                        wb.rx_snr = 5.0f
                                        wb.decoded =
                                            Data.Builder()
                                                .also { wb ->
                                                    wb.portnum = PortNum.TEXT_MESSAGE_APP
                                                    wb.payload = "Hi".encodeUtf8()
                                                }
                                                .build()
                                    }
                                    .build()
                        }
                        .build(),
                )
            },
        )
        val buffer = Buffer()

        useCase(buffer, 1)

        val rows = buffer.readUtf8().lines().drop(1).filter { it.isNotEmpty() }
        assertEquals(senders.map { it.toString() }, rows.map { it.split("\",\"")[2] })
    }

    private fun textLog(from: Int, payload: String) = MeshLog(
        uuid = "1",
        message_type = "TEXT",
        received_date = 1000000000L,
        raw_message = "",
        fromRadio =
        FromRadio.Builder()
            .also { wb ->
                wb.packet =
                    MeshPacket.Builder()
                        .also { pb ->
                            pb.from = from
                            pb.rx_snr = 5.0f
                            pb.decoded =
                                Data.Builder()
                                    .also { db ->
                                        db.portnum = PortNum.TEXT_MESSAGE_APP
                                        db.payload = payload.encodeUtf8()
                                    }
                                    .build()
                        }
                        .build()
            }
            .build(),
    )

    private suspend fun exportRow(): String {
        val buffer = Buffer()
        useCase(buffer, 1)
        return buffer.readUtf8().lines().drop(1).first { it.isNotEmpty() }
    }

    @Test
    fun `sender name with a quote stays one cell with the quote doubled`() = runTest {
        nodeRepository.setNodes(listOf(Node(num = 1234, user = User.Builder().also { it.long_name = "A\",B" }.build())))
        meshLogRepository.setLogs(listOf(textLog(1234, "Hello")))

        assertTrue(exportRow().contains(",\"1234\",\"A\"\",B\","))
    }

    @Test
    fun `sender name starting with a formula character is prefixed`() = runTest {
        nodeRepository.setNodes(
            listOf(Node(num = 1234, user = User.Builder().also { it.long_name = "=SUM(A1)" }.build())),
        )
        meshLogRepository.setLogs(listOf(textLog(1234, "Hello")))

        assertTrue(exportRow().contains(",\"1234\",\"'=SUM(A1)\","))
    }

    @Test
    fun `payload starting with a formula character is prefixed`() = runTest {
        listOf("=1+1", "+1", "-1", "@x", "\tx", "\rx").forEach { payload ->
            meshLogRepository.setLogs(listOf(textLog(1234, payload)))
            val buffer = Buffer()
            useCase(buffer, 1)
            val output = buffer.readUtf8()
            assertTrue(output.endsWith(",\"'$payload\"\n"), "payload ${payload.encodeUtf8().hex()} not prefixed")
        }
    }

    @Test
    fun `payload with embedded quotes and plain text is only quote doubled`() = runTest {
        meshLogRepository.setLogs(listOf(textLog(1234, "say \"hi\" - ok")))

        assertTrue(exportRow().endsWith(",\"say \"\"hi\"\" - ok\""))
    }

    @Test
    fun `negative coordinates stay unprefixed numeric cells`() = runTest {
        val nodeInfoLog =
            MeshLog(
                uuid = "0",
                message_type = "NodeInfo",
                received_date = 1000000000L,
                raw_message = "",
                fromRadio =
                FromRadio.Builder()
                    .also { wb ->
                        wb.node_info =
                            NodeInfo.Builder()
                                .also { nb ->
                                    nb.num = 1234
                                    nb.position =
                                        org.meshtastic.proto.Position.Builder()
                                            .also {
                                                it.latitude_i = -123456789
                                                it.longitude_i = -987654321
                                            }
                                            .build()
                                }
                                .build()
                    }
                    .build(),
            )
        meshLogRepository.setLogs(listOf(nodeInfoLog, textLog(1234, "Hello")))

        val row = exportRow()
        assertTrue(row.contains(",\"-12.34567"), row)
        assertTrue(row.contains(",\"-98.76543"), row)
        assertFalse(row.contains("'-"))
    }
}
