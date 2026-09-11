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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.testing.setupTestContext
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class CommonNodeInfoDaoTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var dao: NodeInfoDao

    private val myNodeInfo: MyNodeEntity =
        MyNodeEntity(
            myNodeNum = 42424242,
            model = "TBEAM",
            firmwareVersion = "2.5.0",
            couldUpdate = false,
            shouldUpdate = false,
            currentPacketId = 1L,
            messageTimeoutMsec = 300000,
            minAppVersion = 1,
            maxChannels = 8,
            hasWifi = false,
        )

    suspend fun createDb() {
        setupTestContext()
        database = getInMemoryDatabaseBuilder().build()
        dao = database.nodeInfoDao()
        dao.setMyNodeInfo(myNodeInfo)
    }

    @AfterTest
    fun closeDb() {
        database.close()
    }

    @Test
    fun testGetMyNodeInfo() = runTest {
        createDb()
        val info = dao.getMyNodeInfo().first()
        assertNotNull(info)
        assertEquals(myNodeInfo.myNodeNum, info.myNodeNum)
    }

    @Test
    fun testUpsertNode() = runTest {
        createDb()
        val node =
            NodeEntity(
                num = 1234,
                user = User(long_name = "Test Node", id = "!test", hw_model = org.meshtastic.proto.HardwareModel.TBEAM),
                lastHeard = (nowMillis / 1000).toInt(),
            )
        dao.upsert(node)
        val result = dao.getNodeByNum(1234)
        assertNotNull(result)
        assertEquals("Test Node", result.node.longName)
    }

    @Test
    fun testNodeDBbyNum() = runTest {
        createDb()
        val node1 = NodeEntity(num = 1, user = User(id = "!1"))
        val node2 = NodeEntity(num = 2, user = User(id = "!2"))
        dao.putAll(listOf(node1, node2))

        val nodes = dao.nodeDBbyNum().first()
        assertEquals(2, nodes.size)
        assertTrue(nodes.containsKey(1))
        assertTrue(nodes.containsKey(2))
    }

    @Test
    fun testDeleteNode() = runTest {
        createDb()
        val node = NodeEntity(num = 1, user = User(id = "!1"))
        dao.upsert(node)
        dao.deleteNode(1)
        val result = dao.getNodeByNum(1)
        assertEquals(null, result)
    }

    @Test
    fun `a remote node changing its key keeps the stored key and records the refusal`() = runTest {
        createDb()
        val trusted = ByteArray(32) { 1 }.toByteString()
        val substitute = ByteArray(32) { 2 }.toByteString()
        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = trusted)))
        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = substitute)))

        // First-wins: anyone can broadcast a NodeInfo under another node's number, so the substitute is refused
        // rather than applied. Overwriting would break PKC direct messages to that contact.
        val stored = dao.getNodeByNum(1)?.node
        assertEquals(trusted, stored?.publicKey)
        assertEquals(trusted, stored?.user?.public_key)
        assertFalse(stored?.keyMatch ?: true)
        assertEquals(substitute, stored?.newPublicKey)
    }

    @Test
    fun `the refused key is kept so the mismatch can name it`() = runTest {
        createDb()
        val trusted = ByteArray(32) { 1 }.toByteString()
        val substitute = ByteArray(32) { 2 }.toByteString()
        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = trusted)))

        // Nothing is refused yet, so there is no key to report.
        assertEquals(null, dao.getNodeByNum(1)?.node?.newPublicKey)

        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = substitute)))
        assertEquals(substitute, dao.getNodeByNum(1)?.node?.newPublicKey)

        // The key already on file arriving again settles nothing: the refusal stands until the connected radio
        // speaks for itself, or the next legitimate beacon would hide the substitute.
        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = trusted)))
        val stillFlagged = dao.getNodeByNum(1)?.node
        assertEquals(trusted, stillFlagged?.publicKey)
        assertFalse(stillFlagged?.keyMatch ?: true)
        assertEquals(substitute, stillFlagged?.newPublicKey)
    }

    @Test
    fun `the connected radio re-keying clears the refused key along with the mismatch`() = runTest {
        createDb()
        val own = myNodeInfo.myNodeNum
        val before = ByteArray(32) { 1 }.toByteString()
        dao.upsert(NodeEntity(num = own, user = User(id = "!own", public_key = before)))
        dao.upsert(NodeEntity(num = own, user = User(id = "!own", public_key = ByteArray(32) { 9 }.toByteString())))
        assertFalse(dao.getNodeByNum(own)?.node?.keyMatch ?: true)

        // The local link is authoritative, so accepting the radio's own key also drops what was refused.
        val after = ByteArray(32) { 2 }.toByteString()
        dao.installConfig(myNodeInfo, listOf(NodeEntity(num = own, user = User(id = "!own", public_key = after))))

        val stored = dao.getNodeByNum(own)?.node
        assertEquals(after, stored?.publicKey)
        assertTrue(stored?.keyMatch ?: false)
        assertEquals(null, stored?.newPublicKey)
    }

    @Test
    fun `the legacy mismatch sentinel arriving as a key is neither refused nor stored`() = runTest {
        createDb()
        val trusted = ByteArray(32) { 1 }.toByteString()
        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = trusted)))

        // A row that recorded a mismatch the old way carries the sentinel as its key. Re-upserting it through the
        // repository must not read as a fresh substitution, and the sentinel is not a key anyone refused.
        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = NodeEntity.ERROR_BYTE_STRING)))
        val remote = dao.getNodeByNum(1)?.node
        assertEquals(trusted, remote?.publicKey)
        assertTrue(remote?.keyMatch ?: false)
        assertEquals(null, remote?.newPublicKey)

        // Nor may the local link write it over the connected radio's real key. A key of its own, or the new-node
        // guard would read this upsert as node 1 claiming a second number and never insert it.
        val own = myNodeInfo.myNodeNum
        val ownKey = ByteArray(32) { 3 }.toByteString()
        dao.upsert(NodeEntity(num = own, user = User(id = "!own", public_key = ownKey)))
        dao.installConfig(
            myNodeInfo,
            listOf(NodeEntity(num = own, user = User(id = "!own", public_key = NodeEntity.ERROR_BYTE_STRING))),
        )
        assertEquals(ownKey, dao.getNodeByNum(own)?.node?.publicKey)
    }

    @Test
    fun `the stored key surviving a substitution still reads as a mismatch to the UI`() = runTest {
        createDb()
        val trusted = ByteArray(32) { 1 }.toByteString()
        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = trusted)))
        dao.upsert(NodeEntity(num = 1, user = User(id = "!1", public_key = ByteArray(32) { 2 }.toByteString())))

        assertTrue(dao.getNodeByNum(1)!!.toModel().mismatchKey)
    }

    @Test
    fun `the connected radio re-keying replaces its stored key rather than flagging a mismatch`() = runTest {
        createDb()
        val own = myNodeInfo.myNodeNum
        val before = ByteArray(32) { 1 }.toByteString()
        // What a 2.8 upgrade or a factory reset does: the same radio comes back under a new key.
        val after = ByteArray(32) { 2 }.toByteString()
        dao.upsert(NodeEntity(num = own, user = User(id = "!own", public_key = before)))

        // Through installConfig, which is the local link. selfNum comes from the device's own MyNodeInfo there.
        dao.installConfig(myNodeInfo, listOf(NodeEntity(num = own, user = User(id = "!own", public_key = after))))

        val stored = dao.getNodeByNum(own)?.node
        assertEquals(after, stored?.publicKey)
        assertEquals(after, stored?.user?.public_key)
        assertTrue(stored?.keyMatch ?: false)
    }

    @Test
    fun `a mesh packet claiming the local node number cannot replace the stored key`() = runTest {
        createDb()
        val own = myNodeInfo.myNodeNum
        val real = ByteArray(32) { 1 }.toByteString()
        dao.upsert(NodeEntity(num = own, user = User(id = "!own", public_key = real)))

        // The single-upsert path carries mesh-received NodeInfo, and `from` is attacker controlled, so matching the
        // local node number proves only that the sender claimed it.
        dao.upsert(NodeEntity(num = own, user = User(id = "!own", public_key = ByteArray(32) { 9 }.toByteString())))

        // First-wins keeps the stored key; the refusal is recorded and still reads as a mismatch to the UI.
        val stored = dao.getNodeByNum(own)
        assertEquals(real, stored?.node?.publicKey)
        assertFalse(stored?.node?.keyMatch ?: true)
        assertTrue(stored!!.toModel().mismatchKey)
    }

    @Test
    fun testClearNodeInfo() = runTest {
        createDb()
        val node1 = NodeEntity(num = 1, user = User(id = "!1"), isFavorite = true)
        val node2 = NodeEntity(num = 2, user = User(id = "!2"), isFavorite = false)
        dao.putAll(listOf(node1, node2))

        dao.clearNodeInfo(preserveFavorites = true)
        val nodes = dao.nodeDBbyNum().first()
        assertEquals(1, nodes.size)
        assertTrue(nodes.containsKey(1))
    }
}
