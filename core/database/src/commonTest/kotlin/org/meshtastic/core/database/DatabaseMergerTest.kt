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
package org.meshtastic.core.database

import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.database.entity.TracerouteNodePositionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies [DatabaseMerger] folds a secondary transport's DB into the canonical one for the same node: message history
 * combines with fresh non-colliding ids and stays searchable, node notes are preserved without clobbering, and
 * reactions dedupe on their composite key. This is the data-integrity path for cross-transport DB unification.
 */
class DatabaseMergerTest {

    // Same physical node reached over two transports → same myNodeNum in both DBs.
    private val nodeNum = 0x1234

    private val source: MeshtasticDatabase = getInMemoryDatabaseBuilder().build()
    private val dest: MeshtasticDatabase = getInMemoryDatabaseBuilder().build()

    @AfterTest
    fun tearDown() {
        source.close()
        dest.close()
    }

    private fun myNode() = MyNodeEntity(
        myNodeNum = nodeNum,
        model = "TBEAM",
        firmwareVersion = "2.5.0",
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 1L,
        messageTimeoutMsec = 300_000,
        minAppVersion = 1,
        maxChannels = 8,
        hasWifi = false,
    )

    private fun textPacket(contact: String, text: String, time: Long) = Packet(
        uuid = 0L, // auto-generated
        myNodeNum = nodeNum,
        port_num = PortNum.TEXT_MESSAGE_APP.value,
        contact_key = contact,
        received_time = time,
        read = true,
        data =
        DataPacket(
            to = NodeAddress.ID_BROADCAST,
            bytes = text.encodeToByteArray().toByteString(),
            dataType = PortNum.TEXT_MESSAGE_APP.value,
        ),
        messageText = text, // FTS indexes this column
    )

    private fun node(num: Int, notes: String = "") = NodeEntity(
        num = num,
        user = User(id = "!$num", long_name = "Node $num", hw_model = HardwareModel.TBEAM),
        notes = notes,
    )

    private fun logEntry(uuid: String, portNum: Int, time: Long) = MeshLog(
        uuid = uuid,
        message_type = "Packet",
        received_date = time,
        raw_message = "",
        fromNum = 30,
        portNum = portNum,
    )

    @Test
    fun mergeFoldsSecondaryTransportIntoCanonical() = runTest {
        // Canonical (dest): one message, a noted node (#10), an un-noted node (#20), a contact setting, a reaction.
        dest.nodeInfoDao().setMyNodeInfo(myNode())
        dest.packetDao().insert(textPacket("0!broadcast", "destmsg", time = 100))
        dest.nodeInfoDao().upsert(node(10, notes = "keep me"))
        dest.nodeInfoDao().upsert(node(20)) // no notes yet
        dest.packetDao().upsertContactSettings(listOf(ContactSettings(contact_key = "0!broadcast", muteUntil = 999)))
        dest
            .packetDao()
            .insert(ReactionEntity(myNodeNum = nodeNum, replyId = 1, userId = "!u", emoji = "👍", timestamp = 1))
        dest.meshLogDao().insert(logEntry("destlog", PortNum.TELEMETRY_APP.value, time = 50))
        dest
            .nodeInfoDao()
            .upsert(MetadataEntity(num = 10, proto = DeviceMetadata(), timestamp = 1000)) // newer than src

        // Secondary (source): two messages, notes for #20 (dest blank → fill) and a source-only node (#30 → insert),
        // the same reaction (dedupe) plus a new one, and a conflicting contact setting (dest's must win).
        source.nodeInfoDao().setMyNodeInfo(myNode())
        source.packetDao().insert(textPacket("0!broadcast", "srcmsgone", time = 200))
        source.packetDao().insert(textPacket("0!broadcast", "srcmsgtwo", time = 300))
        source.nodeInfoDao().upsert(node(20, notes = "src note"))
        source.nodeInfoDao().upsert(node(30, notes = "src-only note"))
        source.packetDao().upsertContactSettings(listOf(ContactSettings(contact_key = "0!broadcast", muteUntil = 1)))
        source
            .packetDao()
            .insert(ReactionEntity(myNodeNum = nodeNum, replyId = 1, userId = "!u", emoji = "👍", timestamp = 1))
        source
            .packetDao()
            .insert(ReactionEntity(myNodeNum = nodeNum, replyId = 1, userId = "!v", emoji = "❤️", timestamp = 2))
        // Telemetry + position history (audit log) plus a traceroute position referencing a log entry.
        source.meshLogDao().insert(logEntry("srctelemetry", PortNum.TELEMETRY_APP.value, time = 200))
        source.meshLogDao().insert(logEntry("srcposition", PortNum.POSITION_APP.value, time = 210))
        source
            .tracerouteNodePositionDao()
            .insertAll(
                listOf(
                    TracerouteNodePositionEntity(
                        logUuid = "srcposition",
                        requestId = 7,
                        nodeNum = 30,
                        position = Position(),
                    ),
                ),
            )
        source.nodeInfoDao().upsert(node(40)) // source-only node, no notes → still brought over
        source
            .nodeInfoDao()
            .upsert(MetadataEntity(num = 10, proto = DeviceMetadata(), timestamp = 500)) // older → dest wins
        source
            .nodeInfoDao()
            .upsert(MetadataEntity(num = 30, proto = DeviceMetadata(), timestamp = 500)) // dest lacks → added
        val srcSession =
            source
                .discoveryDao()
                .insertSession(
                    DiscoverySessionEntity(timestamp = 1, presetsScanned = "LongFast", homePreset = "LongFast"),
                )
        val srcPreset =
            source
                .discoveryDao()
                .insertPresetResult(DiscoveryPresetResultEntity(sessionId = srcSession, presetName = "LongFast"))
        source
            .discoveryDao()
            .insertDiscoveredNodes(listOf(DiscoveredNodeEntity(presetResultId = srcPreset, nodeNum = 30L)))

        DatabaseMerger.merge(source, dest)

        // Packets: 1 + 2, with fresh distinct non-zero uuids (no collision with dest's existing uuid).
        val packets = dest.packetDao().getAllPacketsSnapshot()
        assertEquals(3, packets.size, "all messages combined")
        assertEquals(3, packets.map { it.uuid }.toSet().size, "uuids are unique")
        assertTrue(packets.none { it.uuid == 0L }, "every merged packet got a real id")

        // Merged messages remain searchable via the rebuilt FTS index.
        assertEquals(1, dest.packetDao().searchMessages("srcmsgone").size, "copied message is searchable")

        // Node notes: dest's existing note kept, blank note filled from source, source-only node brought over.
        assertEquals("keep me", dest.nodeInfoDao().getNodeByNum(10)?.node?.notes)
        assertEquals("src note", dest.nodeInfoDao().getNodeByNum(20)?.node?.notes)
        assertNotNull(dest.nodeInfoDao().getNodeByNum(30), "source-only noted node inserted")

        // Reactions dedupe on composite PK: R1 (already in dest) + R2 (new) = 2.
        assertEquals(2, dest.packetDao().getAllReactionsSnapshot().size)

        // Contact settings: destination's value wins on conflict (IGNORE), never overwritten.
        assertEquals(
            999,
            dest.packetDao().getAllContactSettingsSnapshot().first { it.contact_key == "0!broadcast" }.muteUntil,
        )

        // Telemetry/position history lives in the audit log: both transports' entries combine, and traceroute
        // positions come along with their (now-present) parent log rows.
        assertEquals(3, dest.meshLogDao().getAllLogsSnapshot().size, "log history from both transports combined")
        assertEquals(1, dest.tracerouteNodePositionDao().getAllSnapshot().size, "traceroute positions merged")

        // Source-only nodes are brought over even without notes.
        assertNotNull(dest.nodeInfoDao().getNodeByNum(40), "source-only node brought over")

        // Metadata: newest timestamp wins on overlap, source-only rows added.
        val metadata = dest.nodeInfoDao().getAllMetadataSnapshot().associateBy { it.num }
        assertEquals(1000, metadata[10]?.timestamp, "dest's newer metadata kept")
        assertNotNull(metadata[30], "source-only metadata inserted")

        // Discovery: the session → preset → node chain is appended with rewired foreign keys.
        val sessions = dest.discoveryDao().getAllSessionsSnapshot()
        assertEquals(1, sessions.size, "discovery session appended")
        val presets = dest.discoveryDao().getPresetResults(sessions.first().id)
        assertEquals(1, presets.size, "preset appended under rewired session id")
        assertEquals(
            1,
            dest.discoveryDao().getDiscoveredNodes(presets.first().id).size,
            "node appended under rewired preset id",
        )
    }
}
