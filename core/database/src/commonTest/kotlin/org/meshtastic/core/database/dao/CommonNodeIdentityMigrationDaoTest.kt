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
package org.meshtastic.core.database.dao

import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.crc32
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the device-identity reconciliation in [NodeInfoDao.installConfig]: firmware 2.8 renumbers every device
 * (`my_node_num = crc32(public_key)`), and erases/manual re-keys renumber again — the stale identity must be migrated
 * (or vetoed for provably different hardware) instead of shadowing the new one.
 *
 * Abstract, per repo convention: the androidHostTest subclass supplies the Robolectric runner (Room's android target
 * needs real `android.database` exception classes for its upsert fallback), and a jvmTest subclass runs it on the JVM
 * target.
 */
abstract class CommonNodeIdentityMigrationDaoTest {

    private lateinit var database: MeshtasticDatabase
    private lateinit var dao: NodeInfoDao
    private lateinit var packetDao: PacketDao

    // Ingestion hex-encodes device_id, and validDeviceIdOrNull requires that shape.
    private val deviceIdA = "a1b2c3d4e5f60718a9b0c1d2e3f40516"
    private val deviceIdB = "ffeeddccbbaa99887766554433221100"

    private val keyA = ByteArray(32) { 1 }.toByteString()
    private val keyB = ByteArray(32) { 2 }.toByteString()

    private val oldNum = 100
    private val oldId = NodeAddress.numToDefaultId(oldNum)

    private fun myNodeEntity(num: Int, deviceId: String? = MyNodeEntity.DEVICE_ID_UNKNOWN) = MyNodeEntity(
        myNodeNum = num,
        model = "TBEAM",
        firmwareVersion = "2.8.0",
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 1L,
        messageTimeoutMsec = 300000,
        minAppVersion = 1,
        maxChannels = 8,
        hasWifi = false,
        deviceId = deviceId,
    )

    private fun nodeEntity(
        num: Int,
        key: ByteString,
        longName: String = "Node $num",
        notes: String = "",
        isFavorite: Boolean = false,
    ) = NodeEntity(
        num = num,
        user =
        User(
            id = NodeAddress.numToDefaultId(num),
            long_name = longName,
            short_name = longName.takeLast(4),
            hw_model = HardwareModel.TBEAM,
            public_key = key,
        ),
        notes = notes,
        isFavorite = isFavorite,
        lastHeard = 1000,
    )

    private fun packet(owner: Int, contactKey: String) = Packet(
        uuid = 0L,
        myNodeNum = owner,
        port_num = PortNum.TEXT_MESSAGE_APP.value,
        contact_key = contactKey,
        received_time = 1000L,
        read = true,
        data =
        DataPacket(
            to = NodeAddress.ID_BROADCAST,
            bytes = "hello".encodeToByteArray().toByteString(),
            dataType = PortNum.TEXT_MESSAGE_APP.value,
        ),
    )

    private suspend fun createDb(myNode: MyNodeEntity) {
        database = getInMemoryDatabaseBuilder().build()
        dao = database.nodeInfoDao()
        packetDao = database.packetDao()
        dao.setMyNodeInfo(myNode)
    }

    @AfterTest
    fun closeDb() {
        database.close()
    }

    @Test
    fun renumberMigratesHistoryAndLocalState() = runTest {
        createDb(myNodeEntity(oldNum))
        dao.upsert(nodeEntity(oldNum, keyA, notes = "my notes", isFavorite = true))
        packetDao.insert(packet(oldNum, contactKey = "0$oldId")) // note-to-self thread
        packetDao.insert(packet(oldNum, contactKey = "1!deadbeef")) // DM with a peer
        packetDao.insert(
            ReactionEntity(myNodeNum = oldNum, replyId = 7, userId = "!deadbeef", emoji = "x", timestamp = 1L),
        )

        val newNum = 200
        val newId = NodeAddress.numToDefaultId(newNum)
        // Key changed too (manual regen / erase): continuity must not depend on the key.
        val removed = dao.installConfig(myNodeEntity(newNum), listOf(nodeEntity(newNum, keyB)))

        assertEquals(listOf(oldNum), removed)
        assertEquals(newNum, dao.getMyNodeEntity()?.myNodeNum)
        assertNull(dao.getNodeByNum(oldNum), "stale self row must be dropped")

        val newSelf = assertNotNull(dao.getNodeByNum(newNum)).node
        assertEquals("my notes", newSelf.notes, "app-local notes must follow the device")
        assertTrue(newSelf.isFavorite, "favorite flag must follow the device")

        val packets = packetDao.getAllPacketsSnapshot()
        assertTrue(packets.isNotEmpty())
        assertTrue(packets.all { it.myNodeNum == newNum }, "message history must be re-scoped to the new num")
        assertTrue(packets.any { it.contact_key == "0$newId" }, "note-to-self thread must follow the new id")
        assertTrue(packets.any { it.contact_key == "1!deadbeef" }, "peer DM threads must be untouched")
        assertTrue(packetDao.getAllReactionsSnapshot().all { it.myNodeNum == newNum })
    }

    @Test
    fun renumberDropsStaleSelfListedInSameBatch() = runTest {
        createDb(myNodeEntity(oldNum))
        dao.upsert(nodeEntity(oldNum, keyA))

        val newNum = 200
        // A device mid-transition can still stream its old identity alongside the new one.
        val removed =
            dao.installConfig(myNodeEntity(newNum), listOf(nodeEntity(oldNum, keyA), nodeEntity(newNum, keyA)))

        assertEquals(listOf(oldNum), removed)
        assertNull(dao.getNodeByNum(oldNum), "old identity in the same batch must not be resurrected")
        assertNotNull(dao.getNodeByNum(newNum))
    }

    @Test
    fun sameHardwareEraseMigratesAcrossKeyChange() = runTest {
        createDb(myNodeEntity(oldNum, deviceId = deviceIdA))
        dao.upsert(nodeEntity(oldNum, keyA, notes = "keep me"))
        packetDao.insert(packet(oldNum, contactKey = "1!deadbeef"))

        val newNum = 300
        val removed = dao.installConfig(myNodeEntity(newNum, deviceId = deviceIdA), listOf(nodeEntity(newNum, keyB)))

        assertEquals(listOf(oldNum), removed)
        assertNull(dao.getNodeByNum(oldNum))
        assertEquals("keep me", assertNotNull(dao.getNodeByNum(newNum)).node.notes)
        assertTrue(packetDao.getAllPacketsSnapshot().all { it.myNodeNum == newNum })
    }

    @Test
    fun differentHardwareVetoesHistoryMigration() = runTest {
        createDb(myNodeEntity(oldNum, deviceId = deviceIdA))
        dao.upsert(nodeEntity(oldNum, keyA, notes = "old device"))
        packetDao.insert(packet(oldNum, contactKey = "1!deadbeef"))

        val newNum = 300
        val removed = dao.installConfig(myNodeEntity(newNum, deviceId = deviceIdB), listOf(nodeEntity(newNum, keyB)))

        assertEquals(listOf(oldNum), removed)
        assertNull(dao.getNodeByNum(oldNum), "stale self row is dropped even when history is not migrated")
        val newSelf = assertNotNull(dao.getNodeByNum(newNum)).node
        assertEquals("", newSelf.notes, "different hardware must not inherit app-local state")
        assertTrue(
            packetDao.getAllPacketsSnapshot().all { it.myNodeNum == oldNum },
            "history of different hardware stays scoped under the old num",
        )
    }

    @Test
    fun sameNumInstallLeavesEverythingInPlace() = runTest {
        createDb(myNodeEntity(oldNum))
        dao.upsert(nodeEntity(oldNum, keyA, notes = "steady"))
        packetDao.insert(packet(oldNum, contactKey = "1!deadbeef"))

        val removed = dao.installConfig(myNodeEntity(oldNum), listOf(nodeEntity(oldNum, keyA)))

        assertEquals(emptyList(), removed)
        assertEquals("steady", assertNotNull(dao.getNodeByNum(oldNum)).node.notes)
        assertTrue(packetDao.getAllPacketsSnapshot().all { it.myNodeNum == oldNum })
    }

    @Test
    fun installTrustsDeviceReportedKeyChangeForSelf() = runTest {
        createDb(myNodeEntity(oldNum))
        dao.upsert(nodeEntity(oldNum, keyA))

        // Same num, new key (2.7-line erase keeps the MAC-derived num). Without the install-time
        // trust this would poison the local node's key to ERROR_BYTE_STRING and break PKI traffic.
        dao.installConfig(myNodeEntity(oldNum), listOf(nodeEntity(oldNum, keyB)))

        val self = assertNotNull(dao.getNodeByNum(oldNum)).node
        assertEquals(keyB, self.publicKey)
    }

    @Test
    fun canonicalNeighborRenumberMigratesInInstallBatch() = runTest {
        createDb(myNodeEntity(oldNum))
        val peerOldNum = 500
        val peerOldId = NodeAddress.numToDefaultId(peerOldNum)
        dao.upsert(nodeEntity(peerOldNum, keyB, notes = "peer note", isFavorite = true))
        packetDao.insert(packet(oldNum, contactKey = "0$peerOldId"))

        val peerNewNum = keyB.crc32().toInt() // the firmware 2.8 canonical num for this key
        val peerNewId = NodeAddress.numToDefaultId(peerNewNum)
        dao.installConfig(myNodeEntity(oldNum), listOf(nodeEntity(oldNum, keyA), nodeEntity(peerNewNum, keyB)))

        assertNull(dao.getNodeByNum(peerOldNum), "peer's pre-2.8 row must be migrated away")
        val migrated = assertNotNull(dao.getNodeByNum(peerNewNum)).node
        assertEquals("peer note", migrated.notes)
        assertTrue(migrated.isFavorite)
        assertTrue(
            packetDao.getAllPacketsSnapshot().any { it.contact_key == "0$peerNewId" },
            "the DM thread must follow the peer's new id",
        )
    }

    @Test
    fun nonCanonicalKeyConflictKeepsExistingIdentity() = runTest {
        createDb(myNodeEntity(oldNum))
        val peerNum = 500
        dao.upsert(nodeEntity(peerNum, keyB))

        val impostorNum = 600 // not crc32(keyB): an arbitrary num claiming a known key
        dao.installConfig(myNodeEntity(oldNum), listOf(nodeEntity(impostorNum, keyB)))

        assertNotNull(dao.getNodeByNum(peerNum), "existing identity must be kept on a non-canonical conflict")
        assertNull(dao.getNodeByNum(impostorNum), "non-canonical claimant must not be inserted")
    }

    @Test
    fun singleUpsertNeverMigratesEvenForCanonicalNum() = runTest {
        createDb(myNodeEntity(oldNum))
        val peerOldNum = 500
        dao.upsert(nodeEntity(peerOldNum, keyB, isFavorite = true))

        // Mesh-time upserts are unauthenticated: even a canonical crc32(key) num must not migrate
        // identity — that only happens through installConfig (trusted local link).
        val peerNewNum = keyB.crc32().toInt()
        dao.upsert(nodeEntity(peerNewNum, keyB))

        assertNotNull(dao.getNodeByNum(peerOldNum), "existing identity must be kept on mesh-time conflicts")
        assertNull(dao.getNodeByNum(peerNewNum), "mesh-time claimant must not be inserted")
    }
}
