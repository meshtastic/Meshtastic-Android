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
package org.meshtastic.core.model

import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.error
import org.meshtastic.core.resources.message_delivery_status
import org.meshtastic.core.resources.message_status_delivered
import org.meshtastic.core.resources.message_status_delivered_recipient
import org.meshtastic.core.resources.message_status_failed
import org.meshtastic.core.resources.message_status_relayed_unconfirmed
import org.meshtastic.core.resources.message_status_sending
import org.meshtastic.core.resources.message_status_too_large
import org.meshtastic.core.resources.message_status_unknown
import org.meshtastic.core.resources.routing_error_none
import org.meshtastic.core.resources.unrecognized
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataPacketTest {

    @Test
    fun nodeNumToDefaultId_formatsHexWithPrefix() {
        assertEquals("!1234abcd", NodeAddress.numToDefaultId(0x1234ABCD))
        assertEquals("!ffffffff", NodeAddress.numToDefaultId(NodeAddress.NODENUM_BROADCAST))
    }

    @Test
    fun textConstructor_setsTextPayloadProperties() {
        val packet = DataPacket(to = "!abcdef12", channel = 2, text = "hello mesh", replyId = 99)

        assertEquals("hello mesh", packet.text)
        assertNull(packet.alert)
        assertEquals(PortNum.TEXT_MESSAGE_APP.value, packet.dataType)
        assertEquals("!abcdef12", packet.to)
        assertEquals(2, packet.channel)
        assertEquals(99, packet.replyId)
        assertEquals("hello mesh".encodeUtf8(), packet.bytes)
    }

    @Test
    fun alertProperty_onlyReturnsForAlertPackets() {
        val alertPacket = DataPacket(bytes = "wake up".encodeUtf8(), dataType = PortNum.ALERT_APP.value)

        assertEquals("wake up", alertPacket.alert)
        assertNull(alertPacket.text)
    }

    @Test
    fun equalityAndCopy_preserveDataUntilModified() {
        val packet =
            DataPacket(
                to = "!12345678",
                from = "!87654321",
                bytes = "payload".encodeUtf8(),
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                id = 7,
                status = MessageStatus.ENROUTE,
                hopLimit = 3,
                hopStart = 5,
                wantAck = true,
                channel = 1,
                snr = 4.5f,
                rssi = -70,
                replyId = 11,
                relayNode = 42,
                relays = 2,
                viaMqtt = true,
                transportMechanism = 9,
            )

        val identicalCopy = packet.copy()
        val modifiedCopy = packet.copy(status = MessageStatus.DELIVERED, wantAck = false)

        assertEquals(packet, identicalCopy)
        assertEquals(2, packet.hopsAway)
        assertEquals(MessageStatus.ENROUTE, packet.status)
        assertNotEquals(packet, modifiedCopy)
        assertEquals(MessageStatus.DELIVERED, modifiedCopy.status)
        assertFalse(modifiedCopy.wantAck)
        assertEquals(MessageStatus.ENROUTE, packet.status)
    }

    @Test
    fun hopsAway_isUnknownForInvalidHopValues() {
        assertEquals(-1, DataPacket(bytes = null, dataType = 0, hopStart = 0, hopLimit = 0).hopsAway)
        assertEquals(-1, DataPacket(bytes = null, dataType = 0, hopStart = 2, hopLimit = 3).hopsAway)
    }
}

class MessageTest {

    @Test
    fun messageConstruction_preservesProperties() {
        val originalMessage =
            Message(
                uuid = 1L,
                receivedTime = 10L,
                node = Node.createFallback(0x12345678, "Node"),
                text = "original",
                fromLocal = true,
                time = "10:00",
                read = true,
                status = MessageStatus.RECEIVED,
                routingError = 0,
                packetId = 101,
                emojis = emptyList(),
                snr = 1.5f,
                rssi = -65,
                hopsAway = 1,
                replyId = null,
            )
        val reaction =
            Reaction(
                replyId = 101,
                user = originalMessage.node.user,
                emoji = "👍",
                timestamp = 20L,
                snr = 2.5f,
                rssi = -55,
                hopsAway = 2,
                packetId = 202,
                status = MessageStatus.DELIVERED,
                relayNode = 77,
                relays = 1,
                to = "!12345678",
                channel = 3,
            )

        val message =
            Message(
                uuid = 2L,
                receivedTime = 30L,
                node = originalMessage.node,
                text = "reply",
                fromLocal = false,
                time = "10:01",
                read = false,
                status = MessageStatus.ENROUTE,
                routingError = 0,
                packetId = 202,
                emojis = listOf(reaction),
                snr = 3.5f,
                rssi = -75,
                hopsAway = 3,
                replyId = 101,
                originalMessage = originalMessage,
                viaMqtt = true,
                relayNode = 88,
                relays = 2,
                filtered = true,
                transportMechanism = 4,
            )

        assertEquals(2L, message.uuid)
        assertEquals(30L, message.receivedTime)
        assertEquals(originalMessage.node, message.node)
        assertEquals("reply", message.text)
        assertFalse(message.fromLocal)
        assertFalse(message.read)
        assertEquals(MessageStatus.ENROUTE, message.status)
        assertEquals(202, message.packetId)
        assertEquals(1, message.emojis.size)
        assertEquals(reaction, message.emojis.single())
        assertEquals(101, message.replyId)
        assertEquals(originalMessage, message.originalMessage)
        assertEquals(88, message.relayNode)
        assertEquals(2, message.relays)
        assertEquals(4, message.transportMechanism)
        assertTrue(message.viaMqtt)
        assertTrue(message.filtered)
    }

    private fun messageWith(status: MessageStatus?, routingError: Int = 0, isBroadcast: Boolean = false) = Message(
        uuid = 1L,
        receivedTime = 0L,
        node = Node.createFallback(1, "Node"),
        text = "hello",
        fromLocal = true,
        time = "now",
        read = false,
        status = status,
        routingError = routingError,
        packetId = 1,
        emojis = emptyList(),
        snr = 0f,
        rssi = 0,
        hopsAway = 0,
        replyId = null,
        isBroadcast = isBroadcast,
    )

    @Test
    fun getStatusStringRes_channelImplicitAck_readsDeliveredToMesh() {
        val (title, text) = messageWith(MessageStatus.DELIVERED, isBroadcast = true).getStatusStringRes()

        assertEquals(Res.string.message_delivery_status, title)
        assertEquals(Res.string.message_status_delivered, text)
    }

    @Test
    fun getStatusStringRes_dmImplicitAck_readsRelayedUnconfirmed() {
        val (_, text) = messageWith(MessageStatus.DELIVERED, isBroadcast = false).getStatusStringRes()

        assertEquals(Res.string.message_status_relayed_unconfirmed, text)
    }

    @Test
    fun getStatusStringRes_queuedOrEnroute_readsSending() {
        assertEquals(Res.string.message_status_sending, messageWith(MessageStatus.QUEUED).getStatusStringRes().second)
        assertEquals(Res.string.message_status_sending, messageWith(MessageStatus.ENROUTE).getStatusStringRes().second)
    }

    @Test
    fun getStatusStringRes_explicitAck_readsDeliveredToRecipient() {
        val (_, text) = messageWith(MessageStatus.RECEIVED).getStatusStringRes()

        assertEquals(Res.string.message_status_delivered_recipient, text)
    }

    @Test
    fun getStatusStringRes_routingFailure_collapsesToFailed() {
        val (title, text) =
            messageWith(MessageStatus.ERROR, routingError = Routing.Error.NO_ROUTE.value).getStatusStringRes()

        assertEquals(Res.string.error, title)
        assertEquals(Res.string.message_status_failed, text)
    }

    @Test
    fun getStatusStringRes_tooLarge_readsPermanentError() {
        val (_, text) =
            messageWith(MessageStatus.ERROR, routingError = Routing.Error.TOO_LARGE.value).getStatusStringRes()

        assertEquals(Res.string.message_status_too_large, text)
    }

    @Test
    fun isStatusRetryable_offersRetryForDmUnconfirmedAndFailures_butNotTooLarge() {
        assertTrue(messageWith(MessageStatus.DELIVERED, isBroadcast = false).isStatusRetryable())
        assertTrue(messageWith(MessageStatus.ERROR, routingError = Routing.Error.NO_ROUTE.value).isStatusRetryable())
        assertFalse(messageWith(MessageStatus.DELIVERED, isBroadcast = true).isStatusRetryable())
        assertFalse(messageWith(MessageStatus.ERROR, routingError = Routing.Error.TOO_LARGE.value).isStatusRetryable())
        assertFalse(messageWith(MessageStatus.ENROUTE).isStatusRetryable())
        assertFalse(messageWith(MessageStatus.RECEIVED).isStatusRetryable())
    }

    @Test
    fun getStatusStringRes_returnsUnknownForMissingStatus() {
        val message =
            Message(
                uuid = 1L,
                receivedTime = 0L,
                node = Node.createFallback(1, "Node"),
                text = "hello",
                fromLocal = true,
                time = "now",
                read = false,
                status = null,
                routingError = 0,
                packetId = 1,
                emojis = emptyList(),
                snr = 0f,
                rssi = 0,
                hopsAway = 0,
                replyId = null,
            )

        val (title, text) = message.getStatusStringRes()

        assertEquals(Res.string.message_delivery_status, title)
        assertEquals(Res.string.message_status_unknown, text)
    }

    @Test
    fun getStringResFrom_mapsUnknownValuesToUnrecognized() {
        assertEquals(Res.string.routing_error_none, getStringResFrom(Routing.Error.NONE.value))
        assertEquals(Res.string.unrecognized, getStringResFrom(Int.MAX_VALUE))
    }
}
