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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.MeshtasticDatabaseConstructor
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.channelIdentity
import org.meshtastic.core.model.util.planChannelReconciliation
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.PortNum
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Conversations are keyed by channel index, so replacing a slot's occupant used to leave the old channel's messages
 * under the new channel. These cover the re-keying that prevents it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ChannelReconciliationTest {
    private lateinit var database: MeshtasticDatabase
    private lateinit var packetDao: PacketDao

    private val myNodeNum = 42424242

    private val longFast =
        LoRaConfig.Builder()
            .also { wb ->
                wb.use_preset = true
                wb.modem_preset = ModemPreset.LONG_FAST
            }
            .build()
    private val mediumFast =
        LoRaConfig.Builder()
            .also { wb ->
                wb.use_preset = true
                wb.modem_preset = ModemPreset.MEDIUM_FAST
            }
            .build()
    private val defaultPsk = byteArrayOf(0x01).toByteString()
    private val vcfmwPsk = byteArrayOf(0x11, 0x22, 0x33, 0x44).toByteString()

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
        database
            .nodeInfoDao()
            .setMyNodeInfo(
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
                ),
            )
        packetDao = database.packetDao()
    }

    @After fun closeDb() = database.close()

    /** The reported bug: a scanned QR replaces the primary channel and the old mesh's messages stay behind. */
    @Test
    fun `a replaced primary channel takes its history with it`() = runTest {
        insertBroadcast(channel = 0, text = "hello from the old mesh")
        val old = channelSet(longFast, ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build())
        val new =
            channelSet(
                longFast,
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.psk = vcfmwPsk
                        wb.name = "VCFMW"
                    }
                    .build(),
            )

        reconcile(old, new)

        assertEquals(0, messagesOn(ContactKey.broadcast(0).value).size, "primary must start empty")
        val retired = ContactKey.retiredBroadcast(old.settings[0].channelIdentity(longFast).token).value
        assertEquals(1, messagesOn(retired).size, "history must follow the channel it belongs to")
    }

    /**
     * A preset switch changes the effective channel name, so it is a different mesh even though neither the raw name
     * nor the raw PSK moved. Matching on the raw fields missed this entirely.
     */
    @Test
    fun `switching modem preset retires the old channel`() = runTest {
        insertBroadcast(channel = 0, text = "sent on LongFast")
        val settings = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()

        reconcile(channelSet(longFast, settings), channelSet(mediumFast, settings))

        assertEquals(0, messagesOn(ContactKey.broadcast(0).value).size)
        val retired = ContactKey.retiredBroadcast(settings.channelIdentity(longFast).token).value
        assertEquals(1, messagesOn(retired).size)
    }

    @Test
    fun `re-adding a channel restores its history to the live slot`() = runTest {
        insertBroadcast(channel = 0, text = "sent on LongFast", packetId = 100)
        insertReaction(replyId = 100, channel = 0, emoji = "\uD83D\uDC4D")
        val settings = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()

        reconcile(channelSet(longFast, settings), channelSet(mediumFast, settings))
        reconcile(channelSet(mediumFast, settings), channelSet(longFast, settings))

        val live = messagesOn(ContactKey.broadcast(0).value)
        assertEquals(1, live.size, "the channel came back, so its messages must too")
        assertEquals(0, live.single().data.channel, "and be addressable on the live slot again")
        assertEquals("", packetDao.getContactSettings(ContactKey.broadcast(0).value)?.displayName.orEmpty())
        assertEquals(0, packetDao.getAllReactionsSnapshot().single().channel, "its reactions come back with it")
    }

    /**
     * A retired conversation keeps the channel index it was retired from, so its reactions sit on an index a live
     * channel now owns. Reclaiming must lift only its own reactions, which is why they are scoped through the retired
     * conversation's packet ids rather than by channel.
     */
    @Test
    fun `reclaiming lifts only its own reactions off a shared index`() = runTest {
        insertBroadcast(channel = 0, text = "on the old mesh", packetId = 100)
        insertReaction(replyId = 100, channel = 0, emoji = "\uD83D\uDC4D")
        val old =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "Old"
                }
                .build()
        val new =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = vcfmwPsk
                    wb.name = "New"
                }
                .build()

        reconcile(channelSet(longFast, old), channelSet(longFast, new))

        // The replacement channel now owns slot 0 and gathers its own message and reaction there.
        insertBroadcast(channel = 0, text = "on the new mesh", packetId = 200)
        insertReaction(replyId = 200, channel = 0, emoji = "\uD83C\uDF89")

        reconcile(channelSet(longFast, new), channelSet(longFast, new, old))

        val reactions = packetDao.getAllReactionsSnapshot().associateBy { it.replyId }
        assertEquals(1, reactions.getValue(100).channel, "the reclaimed conversation's reaction follows it")
        assertEquals(0, reactions.getValue(200).channel, "the live channel's reaction stays put")
        assertEquals("on the old mesh", messagesOn(ContactKey.broadcast(1).value).single().data.text)
        assertEquals("on the new mesh", messagesOn(ContactKey.broadcast(0).value).single().data.text)
    }

    /**
     * Once a change has settled, recomputing against the new set has to produce nothing to do. That, rather than
     * `applyChannelReconciliation` being idempotent, is what stops a reconnect from undoing the previous swap: the plan
     * is derived from the stored baseline and committed with it in one transaction, so the same plan is never applied
     * twice.
     */
    @Test
    fun `reconciling again once a swap has settled finds nothing to do`() = runTest {
        insertBroadcast(channel = 0, text = "on A")
        insertBroadcast(channel = 1, text = "on B")
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = vcfmwPsk
                    wb.name = "B"
                }
                .build()
        val swapped = channelSet(longFast, b, a)

        reconcile(channelSet(longFast, a, b), swapped)
        reconcile(swapped, swapped)

        assertEquals("on B", messagesOn(ContactKey.broadcast(0).value).single().data.text)
        assertEquals("on A", messagesOn(ContactKey.broadcast(1).value).single().data.text)
    }

    /** A swap must not leave a conversation's mute or pin on the channel it traded places with. */
    @Test
    fun `swapping slots carries each conversation's settings`() = runTest {
        insertBroadcast(channel = 0, text = "on A")
        insertBroadcast(channel = 1, text = "on B")
        packetDao.setPinned(listOf(ContactKey.broadcast(0).value), true)
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = vcfmwPsk
                    wb.name = "B"
                }
                .build()

        reconcile(channelSet(longFast, a, b), channelSet(longFast, b, a))

        assertEquals(true, packetDao.getContactSettings(ContactKey.broadcast(1).value)?.pinned, "A was pinned")
        assertEquals(false, packetDao.getContactSettings(ContactKey.broadcast(0).value)?.pinned ?: false)
    }

    @Test
    fun `swapping two channel slots moves both conversations`() = runTest {
        insertBroadcast(channel = 0, text = "on A", packetId = 100)
        insertBroadcast(channel = 1, text = "on B", packetId = 200)
        insertReaction(replyId = 100, channel = 0, emoji = "👍")
        insertReaction(replyId = 200, channel = 1, emoji = "🎉")
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = vcfmwPsk
                    wb.name = "B"
                }
                .build()

        reconcile(channelSet(longFast, a, b), channelSet(longFast, b, a))

        assertEquals("on B", messagesOn(ContactKey.broadcast(0).value).single().data.text)
        assertEquals("on A", messagesOn(ContactKey.broadcast(1).value).single().data.text)
        val reactions = packetDao.getAllReactionsSnapshot().associateBy { it.replyId }
        assertEquals(1, reactions.getValue(100).channel, "a reaction must follow its message")
        assertEquals(0, reactions.getValue(200).channel)
    }

    /**
     * Deleting the first of two channels retires it and moves the survivor down into its slot. The retirement has to be
     * applied before the move, or the survivor's packets land on the slot the retirement is about to sweep and go into
     * the wrong archive.
     */
    @Test
    fun `deleting the first channel leaves the survivor intact on slot 0`() = runTest {
        insertBroadcast(channel = 0, text = "on A")
        insertBroadcast(channel = 1, text = "on B")
        packetDao.setPinned(listOf(ContactKey.broadcast(1).value), true)
        packetDao.setMuteUntil(listOf(ContactKey.broadcast(1).value), Long.MAX_VALUE)
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = vcfmwPsk
                    wb.name = "B"
                }
                .build()

        reconcile(channelSet(longFast, a, b), channelSet(longFast, b))

        val live = messagesOn(ContactKey.broadcast(0).value)
        assertEquals(1, live.size, "the surviving channel keeps its messages")
        assertEquals("on B", live.single().data.text)
        val settings = assertNotNull(packetDao.getContactSettings(ContactKey.broadcast(0).value))
        assertTrue(settings.pinned, "and its pin")
        assertEquals(Long.MAX_VALUE, settings.muteUntil, "and its mute")

        val retired = ContactKey.retiredBroadcast(a.channelIdentity(longFast).token).value
        assertEquals("on A", messagesOn(retired).single().data.text, "only A is archived")
    }

    /**
     * A message still queued when its channel is archived must never go out. Its stored channel index points at the
     * slot it was composed for, and someone else is on that slot now.
     */
    @Test
    fun `a queued message in an archived conversation is not claimable for sending`() = runTest {
        insertBroadcast(channel = 0, text = "never sent", packetId = 77, status = MessageStatus.QUEUED)
        val old =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()

        reconcile(
            channelSet(longFast, old),
            channelSet(
                longFast,
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.psk = vcfmwPsk
                        wb.name = "B"
                    }
                    .build(),
            ),
        )

        val row = packetDao.getAllPackets(PortNum.TEXT_MESSAGE_APP.value).first().single()
        assertTrue(ContactKey(row.contact_key).isRetired, "precondition: the conversation was archived")
        assertNull(packetDao.claimQueuedPacket(myNodeNum, row.uuid), "an archived conversation cannot be claimed")
        assertEquals(
            MessageStatus.QUEUED,
            packetDao.getAllPackets(PortNum.TEXT_MESSAGE_APP.value).first().single().data.status,
            "and the row is left alone rather than marked enroute",
        )
    }

    /** A direct message is a conversation with a node, so it must survive its channel being replaced. */
    @Test
    fun `direct messages are never retired`() = runTest {
        val dmKey = "0!a1b2c3d4"
        packetDao.insert(
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = PortNum.TEXT_MESSAGE_APP.value,
                contact_key = dmKey,
                received_time = nowMillis,
                read = false,
                data = DataPacket(to = "!a1b2c3d4", channel = 0, text = "a private word"),
            ),
        )

        reconcile(
            channelSet(longFast, ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()),
            channelSet(
                longFast,
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.psk = vcfmwPsk
                        wb.name = "VCFMW"
                    }
                    .build(),
            ),
        )

        assertEquals(1, messagesOn(dmKey).size, "a DM thread belongs to the node, not the channel")
    }

    /** A retired conversation is labelled from its own stored name; its old slot now names someone else. */
    @Test
    fun `retiring stores the old channel name and carries its settings`() = runTest {
        insertBroadcast(channel = 0, text = "hi")
        packetDao.setMuteUntil(listOf(ContactKey.broadcast(0).value), Long.MAX_VALUE)
        packetDao.setPinned(listOf(ContactKey.broadcast(0).value), true)
        val old =
            channelSet(
                longFast,
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.psk = defaultPsk
                        wb.name = "VCFMW"
                    }
                    .build(),
            )

        reconcile(
            old,
            channelSet(
                longFast,
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.psk = vcfmwPsk
                        wb.name = "Field Day"
                    }
                    .build(),
            ),
        )

        val retiredKey = ContactKey.retiredBroadcast(old.settings[0].channelIdentity(longFast).token).value
        val settings = assertNotNull(packetDao.getContactSettings(retiredKey))
        assertEquals("VCFMW", settings.displayName)
        assertTrue(settings.pinned, "pinning follows the conversation")
        assertEquals(Long.MAX_VALUE, settings.muteUntil)
        assertNull(packetDao.getContactSettings(ContactKey.broadcast(0).value), "the new channel starts fresh")
    }

    /** A blank channel name only resolves through the modem preset, so without it identity is a guess. */
    @Test
    fun `an unknown lora config never retires anything`() = runTest {
        insertBroadcast(channel = 0, text = "still mine")
        val settings = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()

        reconcile(
            channelSet(longFast, settings),
            ChannelSet.Builder().also { wb -> wb.settings = listOf(settings) }.build(),
        )

        assertEquals(1, messagesOn(ContactKey.broadcast(0).value).size)
    }

    /** Region is not part of channel identity, and the firmware does not hash it either. */
    @Test
    fun `changing region alone leaves conversations alone`() = runTest {
        insertBroadcast(channel = 0, text = "unaffected")
        val settings = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()

        reconcile(
            channelSet(longFast, settings),
            channelSet(longFast.newBuilder().also { wb -> wb.region = LoRaConfig.RegionCode.EU_868 }.build(), settings),
        )

        assertEquals(1, messagesOn(ContactKey.broadcast(0).value).size)
    }

    private suspend fun reconcile(old: ChannelSet, new: ChannelSet) {
        packetDao.applyChannelReconciliation(planChannelReconciliation(old, new, packetDao.getRetiredContactTokens()))
    }

    private fun channelSet(lora: LoRaConfig, vararg settings: ChannelSettings) = ChannelSet.Builder()
        .also { wb ->
            wb.settings = settings.toList()
            wb.lora_config = lora
        }
        .build()

    private suspend fun insertBroadcast(channel: Int, text: String, packetId: Int = 0, status: MessageStatus? = null) {
        packetDao.insert(
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                port_num = PortNum.TEXT_MESSAGE_APP.value,
                contact_key = ContactKey.broadcast(channel).value,
                received_time = nowMillis,
                read = false,
                data =
                DataPacket(to = NodeAddress.ID_BROADCAST, channel = channel, text = text).let {
                    if (status != null) it.copy(status = status) else it
                },
                packetId = packetId,
            ),
        )
    }

    private suspend fun insertReaction(replyId: Int, channel: Int, emoji: String) {
        packetDao.insert(
            ReactionEntity(
                myNodeNum = myNodeNum,
                replyId = replyId,
                userId = "!00000001",
                emoji = emoji,
                timestamp = nowMillis,
                channel = channel,
                to = NodeAddress.ID_BROADCAST,
            ),
        )
    }

    private suspend fun messagesOn(contactKey: String) =
        packetDao.getAllPackets(PortNum.TEXT_MESSAGE_APP.value).first().filter { it.contact_key == contactKey }
}
