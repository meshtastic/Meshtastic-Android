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

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.MeshtasticDatabaseConstructor
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.PortNum
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies FTS5 full-text message search (#5373) and the historical-message backfill.
 *
 * [backfillMessageTexts_makesHistoricalMessagesSearchable] is the regression guard for the original `json_extract(data,
 * '$.text')` backfill, which silently matched nothing: `DataPacket.text` is a computed property and is never serialized
 * into the stored JSON, so historical messages stayed permanently unsearchable.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PacketFtsSearchTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var packetDao: PacketDao
    private lateinit var nodeInfoDao: NodeInfoDao

    private val myNodeNum = 42424242

    private val myNodeInfo =
        MyNodeEntity(
            myNodeNum = myNodeNum,
            model = null,
            firmwareVersion = null,
            couldUpdate = false,
            shouldUpdate = false,
            currentPacketId = 1L,
            messageTimeoutMsec = 5 * 60 * 1000,
            minAppVersion = 1,
            maxChannels = 8,
            hasWifi = false,
        )

    @Before
    fun createDb(): Unit = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room.inMemoryDatabaseBuilder<MeshtasticDatabase>(
                context = context,
                factory = { MeshtasticDatabaseConstructor.initialize() },
            )
                .setDriver(BundledSQLiteDriver())
                .build()
        nodeInfoDao = database.nodeInfoDao().apply { setMyNodeInfo(myNodeInfo) }
        packetDao = database.packetDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun searchMessages_matchesIndexedText_ignoresNonMatches() = runTest {
        insertTextPacket(contactKey = CONTACT, text = "the quick brown fox", messageText = "the quick brown fox")

        assertEquals(1, packetDao.searchMessages("brown").size, "a term in the indexed message should match")
        assertTrue(packetDao.searchMessages("zebra").isEmpty(), "an absent term should not match")
    }

    @Test
    fun searchMessagesInConversation_scopesToContact() = runTest {
        insertTextPacket(contactKey = CONTACT, text = "shared keyword here", messageText = "shared keyword here")
        insertTextPacket(contactKey = OTHER_CONTACT, text = "shared keyword here", messageText = "shared keyword here")

        assertEquals(2, packetDao.searchMessages("keyword").size, "both conversations match the global search")
        assertEquals(1, packetDao.searchMessagesInConversation("keyword", CONTACT).size, "scoped to one contact")
    }

    @Test
    fun backfillMessageTexts_makesHistoricalMessagesSearchable() = runTest {
        // A pre-v39 packet: the payload carries the text, but message_text was never populated, so it is unindexed.
        insertTextPacket(contactKey = CONTACT, text = "historical needle", messageText = "")

        assertTrue(packetDao.searchMessages("needle").isEmpty(), "the historical message is unindexed before backfill")
        assertEquals(1, packetDao.countPacketsNeedingBackfill(), "the empty-message_text packet needs backfill")

        val updated = packetDao.backfillMessageTexts()
        packetDao.rebuildFtsIndex()

        assertEquals(1, updated, "the historical text packet should be backfilled")
        assertEquals(1, packetDao.searchMessages("needle").size, "the backfilled message should now be searchable")
        assertEquals(0, packetDao.countPacketsNeedingBackfill(), "nothing left to backfill")
    }

    private suspend fun insertTextPacket(contactKey: String, text: String, messageText: String) {
        packetDao.insert(
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = PortNum.TEXT_MESSAGE_APP.value,
                contact_key = contactKey,
                received_time = nowMillis,
                read = false,
                data = DataPacket(to = NodeAddress.ID_BROADCAST, channel = 0, text = text),
                messageText = messageText,
            ),
        )
    }

    companion object {
        private const val CONTACT = "0!aaaa1111"
        private const val OTHER_CONTACT = "0!bbbb2222"
    }
}
