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
@file:Suppress("MagicNumber")

package org.meshtastic.screenshot.marketing

import okio.ByteString.Companion.toByteString
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Contact
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Reaction
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.User
import org.meshtastic.screenshot.marketing.resources.Res
import org.meshtastic.screenshot.marketing.resources.marketing_msg_crossing_low
import org.meshtastic.screenshot.marketing.resources.marketing_msg_heading_up
import org.meshtastic.screenshot.marketing.resources.marketing_msg_lunch_lookout
import org.meshtastic.screenshot.marketing.resources.marketing_msg_made_ridge
import org.meshtastic.screenshot.marketing.resources.marketing_msg_parked
import org.meshtastic.screenshot.marketing.resources.marketing_msg_take_crossing
import org.meshtastic.screenshot.marketing.resources.marketing_msg_truck_around
import org.meshtastic.screenshot.marketing.resources.marketing_msg_weather_window
import org.meshtastic.screenshot.marketing.resources.marketing_preview_first_aid
import org.meshtastic.screenshot.marketing.resources.marketing_preview_see_me_on_map
import org.meshtastic.screenshot.marketing.resources.marketing_preview_spare_battery
import org.meshtastic.screenshot.marketing.resources.marketing_preview_trail_crew
import org.meshtastic.screenshot.marketing.resources.marketing_preview_weather_thanks
import java.time.LocalDate
import java.time.ZoneId

/**
 * One plausible mesh shared by every store screenshot, so the five shots tell one story: a base camp, a ridge-top
 * router, and a handful of hikers, boats and trucks spread across a valley outside Dallas. Nine nodes, so the map never
 * clusters them. "Last heard" and message timestamps hang off the wall clock because the row composables format them
 * relative to now.
 *
 * Node and channel names are literals: they are what a real group typed into their radios, and the channel names feed
 * the QR payload. The prose - the thread, the direct-message previews - is a string resource per line, resolved at
 * composition so each locale renders its own translation once Crowdin has filled it in.
 *
 * Built once per render pass: "last heard" hangs off [now], the wall clock when the mesh was built, and the row
 * composables format it against the wall clock at composition. [heardAgo] places every offset mid-minute, so a label
 * can only change once composition falls more than 30 s behind construction; one locale's pass takes a third of that.
 */
internal class SampleMesh(private val now: Int = (nowMillis / 1_000L).toInt()) {
    companion object {
        const val CENTER_LAT = 32.7767
        const val CENTER_LON = -96.797
        private const val HALF_MINUTE_SECONDS = 30
        private const val JUST_NOW_SECONDS = 5
    }

    /** 0 reads "Now"; anything else lands 30 s into that minute so a delayed composition cannot move the label. */
    private fun heardAgo(minutes: Int): Int = if (minutes == 0) JUST_NOW_SECONDS else minutes * 60 + HALF_MINUTE_SECONDS

    /** A fixed, obviously synthetic 256-bit key so the detail page shows a keyed node rather than a key warning. */
    private val ridgeKey = ByteArray(32) { i -> (i * 13 + 29).toByte() }.toByteString()
    private val summitKey = ByteArray(32) { i -> (i * 17 + 41).toByte() }.toByteString()

    val baseCamp =
        node(
            0x2b3c4d5e,
            "Base Camp",
            "BASE",
            HardwareModel.HELTEC_V3,
            Role.CLIENT,
            95,
            0,
            0,
            0.0,
            0.0,
            0f,
            0,
            favorite = true,
        )
    val ridgeTop =
        node(
            0x1a2b3c4d,
            "Ridge Top",
            "RDGE",
            HardwareModel.RAK4631,
            Role.ROUTER,
            88,
            0,
            2,
            0.04,
            0.03,
            11.5f,
            -68,
            favorite = true,
        )
            .let { base ->
                base.copy(user = base.user.newBuilder().also { it.public_key = ridgeKey }.build(), publicKey = ridgeKey)
            }
            .copy(
                metadata =
                DeviceMetadata.Builder()
                    .also {
                        it.firmware_version = "2.8.1.f3c2a9"
                        it.hw_model = HardwareModel.RAK4631
                        it.role = Role.ROUTER
                        it.hasBluetooth = true
                        it.hasWifi = false
                        it.canShutdown = true
                    }
                    .build(),
                environmentMetrics =
                EnvironmentMetrics.Builder()
                    .also {
                        it.temperature = 17.4f
                        it.relative_humidity = 41f
                        it.barometric_pressure = 862.7f
                    }
                    .build(),
                deviceMetrics =
                DeviceMetrics.Builder()
                    .also {
                        it.battery_level = 88
                        it.voltage = 4.02f
                        it.channel_utilization = 6.8f
                        it.air_util_tx = 2.3f
                        it.uptime_seconds = 19 * 86_400 + 7 * 3_600
                    }
                    .build(),
            )
    val trailhead =
        node(0x3c4d5e6f, "Trailhead", "TRLH", HardwareModel.TBEAM, Role.CLIENT, 72, 1, 3, -0.03, 0.045, 8.25f, -92)
    val riverCrossing =
        node(
            0x4d5e6f70,
            "River Crossing",
            "RIVR",
            HardwareModel.T_ECHO,
            Role.CLIENT_MUTE,
            64,
            1,
            7,
            -0.045,
            -0.02,
            6.0f,
            -101,
        )

    /** A solar router two hops out: the second node with a full detail page, so the wide layouts show two of them. */
    val summitSolar =
        node(
            0x5e6f7081,
            "Summit Solar",
            "SMMT",
            HardwareModel.RAK4631,
            Role.ROUTER,
            91,
            2,
            15,
            0.05,
            -0.04,
            3.5f,
            -110,
            favorite = true,
        )
            .let { base ->
                base.copy(
                    user = base.user.newBuilder().also { it.public_key = summitKey }.build(),
                    publicKey = summitKey,
                )
            }
            .copy(
                metadata =
                DeviceMetadata.Builder()
                    .also {
                        it.firmware_version = "2.8.1.f3c2a9"
                        it.hw_model = HardwareModel.RAK4631
                        it.role = Role.ROUTER
                        it.hasBluetooth = true
                        it.hasWifi = false
                        it.canShutdown = true
                    }
                    .build(),
                environmentMetrics =
                EnvironmentMetrics.Builder()
                    .also {
                        it.temperature = 9.6f
                        it.relative_humidity = 58f
                        it.barometric_pressure = 791.3f
                    }
                    .build(),
                powerMetrics =
                PowerMetrics.Builder()
                    .also {
                        it.ch1_voltage = 13.8f
                        it.ch1_current = 412f
                    }
                    .build(),
                deviceMetrics =
                DeviceMetrics.Builder()
                    .also {
                        it.battery_level = 91
                        it.voltage = 4.09f
                        it.channel_utilization = 5.1f
                        it.air_util_tx = 1.9f
                        it.uptime_seconds = 63 * 86_400 + 2 * 3_600
                    }
                    .build(),
            )
    val kayakDan =
        node(
            0x6f708192,
            "Kayak Dan",
            "KDAN",
            HardwareModel.HELTEC_WIRELESS_TRACKER,
            Role.TRACKER,
            58,
            2,
            25,
            0.012,
            -0.042,
            2.75f,
            -113,
        )
    val fireLookout =
        node(
            0x708192a3,
            "Old Fire Lookout",
            "LOOK",
            HardwareModel.TBEAM,
            Role.CLIENT,
            47,
            3,
            60,
            -0.01,
            0.02,
            -1.5f,
            -118,
        )
    val sarahsTruck =
        node(
            0x8192a3b4.toInt(),
            "Sarah's Truck",
            "SRAH",
            HardwareModel.T_DECK,
            Role.CLIENT,
            83,
            1,
            4,
            0.025,
            0.01,
            9.0f,
            -85,
        )
    val hamShack =
        node(
            0xa3b4c5d6.toInt(),
            "Ham Shack",
            "SHCK",
            HardwareModel.STATION_G2,
            Role.CLIENT,
            77,
            2,
            11,
            -0.02,
            -0.035,
            4.5f,
            -104,
        )

    /** The node list, in the order the real list would show it: us first, then favorites, then by last heard. */
    val nodes: List<Node> =
        listOf(baseCamp, ridgeTop, summitSolar, trailhead, sarahsTruck, riverCrossing, hamShack, kayakDan, fireLookout)

    /** LongTurbo (index 0, default PSK), a private group channel with a full 256-bit key, and a second private one. */
    val channelSet: ChannelSet =
        ChannelSet.Builder()
            .also { set ->
                set.settings =
                    listOf(
                        ChannelSettings.Builder().also { it.psk = byteArrayOf(1).toByteString() }.build(),
                        ChannelSettings.Builder()
                            .also {
                                it.name = "Basecamp"
                                it.psk = ByteArray(32) { i -> (i * 7 + 3).toByte() }.toByteString()
                            }
                            .build(),
                        ChannelSettings.Builder()
                            .also {
                                it.name = "TrailCrew"
                                it.psk = ByteArray(32) { i -> (i * 11 + 5).toByte() }.toByteString()
                            }
                            .build(),
                    )
                set.lora_config =
                    Config.LoRaConfig.Builder()
                        .also {
                            it.use_preset = true
                            it.modem_preset = Config.LoRaConfig.ModemPreset.LONG_TURBO
                            it.region = Config.LoRaConfig.RegionCode.US
                            it.hop_limit = 3
                            it.tx_enabled = true
                        }
                        .build()
            }
            .build()

    private val messageTextByUuid = mutableMapOf<Long, StringResource>()

    /**
     * The LongTurbo thread as seen from Base Camp: a day trip checking in from the ridge and the river. Each
     * [Message.text] is empty here; [messageText] carries the line as a resource, and the screen fills it in at
     * composition.
     */
    val messages: List<Message> =
        listOf(
            received(trailhead, Res.string.marketing_msg_heading_up, "09:02", 39, 5.5f, -92, hops = 1),
            received(sarahsTruck, Res.string.marketing_msg_parked, "09:05", 36, 9.0f, -85, hops = 1),
            sent(Res.string.marketing_msg_weather_window, "09:07", 34),
            received(trailhead, Res.string.marketing_msg_made_ridge, "09:31", 10, 11.25f, -70, hops = 1)
                .withReactions(reaction(baseCamp, "👍", 9), reaction(sarahsTruck, "🔥", 8)),
            received(kayakDan, Res.string.marketing_msg_crossing_low, "09:34", 7, 2.75f, -113, hops = 2),
            sent(Res.string.marketing_msg_take_crossing, "09:35", 6),
            received(sarahsTruck, Res.string.marketing_msg_truck_around, "09:38", 3, 8.5f, -88, hops = 1)
                .withReactions(reaction(trailhead, "❤️", 2)),
            received(trailhead, Res.string.marketing_msg_lunch_lookout, "09:41", 0, 10.0f, -74, hops = 1),
        )

    /** The prose of each message in [messages], by uuid. */
    val messageText: Map<Long, StringResource>
        get() = messageTextByUuid

    /**
     * The conversation list beside the thread on wide layouts: the three channels, then the hikers Base Camp has
     * messaged directly. [Contact.lastMessageText] is empty here for the same reason as [Message.text]; the preview
     * line is in [contactPreview].
     */
    val contacts: List<Contact> =
        listOf(
            channelContact(0, "LongTurbo", at = "09:41", unread = 0),
            channelContact(1, "Basecamp", at = "09:23", unread = 0),
            channelContact(2, "TrailCrew", at = "08:55", unread = 2),
            directContact(trailhead, at = "09:29"),
            directContact(sarahsTruck, at = "09:14", unread = 1),
            directContact(kayakDan, at = "08:46"),
        )

    /**
     * The preview line of each contact in [contacts], by contact key: the sender whose short name the app prefixes the
     * line with (none when Base Camp sent it), and the line itself.
     */
    val contactPreview: Map<String, Pair<Node?, StringResource>> =
        mapOf(
            contacts[0].contactKey to (trailhead to Res.string.marketing_msg_lunch_lookout),
            contacts[1].contactKey to (sarahsTruck to Res.string.marketing_preview_spare_battery),
            contacts[2].contactKey to (trailhead to Res.string.marketing_preview_trail_crew),
            contacts[3].contactKey to (trailhead to Res.string.marketing_preview_weather_thanks),
            contacts[4].contactKey to (null to Res.string.marketing_preview_first_aid),
            contacts[5].contactKey to (kayakDan to Res.string.marketing_preview_see_me_on_map),
        )

    /**
     * The row shows a message from today as its clock time, so these are fixed times of day rather than offsets from
     * now, in step with the thread's own "09:02" stamps; an offset would print the wall clock.
     */
    private fun todayAt(time: String): Long {
        val (hour, minute) = time.split(':').map(String::toInt)
        return LocalDate.now().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun channelContact(index: Int, name: String, at: String, unread: Int): Contact = Contact(
        contactKey = ContactKey.broadcast(index).value,
        shortName = index.toString(),
        longName = name,
        lastMessageTime = todayAt(at),
        lastMessageText = "",
        unreadCount = unread,
        messageCount = messages.size,
        isMuted = false,
        isUnmessageable = false,
    )

    private fun directContact(from: Node, at: String, unread: Int = 0): Contact = Contact(
        contactKey = "0${from.user.id}",
        shortName = from.user.short_name,
        longName = from.user.long_name,
        lastMessageTime = todayAt(at),
        lastMessageText = "",
        unreadCount = unread,
        messageCount = 4,
        isMuted = false,
        isUnmessageable = false,
        nodeColors = from.colors,
    )

    /** Reactions render from `MessageItem`'s own list, not [Message.emojis], so they are carried per message here. */
    val reactions: Map<Long, List<Reaction>> = messages.associate { it.uuid to it.emojis }

    private fun Message.withReactions(vararg reactions: Reaction): Message = copy(emojis = reactions.toList())

    private fun reaction(from: Node, emoji: String, minutesAgo: Int): Reaction = Reaction(
        replyId = 0,
        user = from.user,
        emoji = emoji,
        timestamp = nowMillis - minutesAgo * 60_000L,
        snr = 6.0f,
        rssi = -90,
        hopsAway = from.hopsAway,
    )

    private var nextMessageId = 0L

    private fun received(
        from: Node,
        text: StringResource,
        time: String,
        minutesAgo: Int,
        snr: Float,
        rssi: Int,
        hops: Int,
    ): Message = message(
        from,
        text,
        time,
        minutesAgo,
        fromLocal = false,
        status = MessageStatus.RECEIVED,
        snr = snr,
        rssi = rssi,
        hops = hops,
    )

    private fun sent(text: StringResource, time: String, minutesAgo: Int): Message = message(
        baseCamp,
        text,
        time,
        minutesAgo,
        fromLocal = true,
        status = MessageStatus.DELIVERED,
        snr = null,
        rssi = null,
        hops = 0,
    )

    private fun message(
        from: Node,
        text: StringResource,
        time: String,
        minutesAgo: Int,
        fromLocal: Boolean,
        status: MessageStatus,
        snr: Float?,
        rssi: Int?,
        hops: Int,
    ): Message {
        val id = ++nextMessageId
        messageTextByUuid[id] = text
        return Message(
            uuid = id,
            receivedTime = nowMillis - minutesAgo * 60_000L,
            node = from,
            text = "",
            fromLocal = fromLocal,
            time = time,
            read = true,
            status = status,
            routingError = 0,
            packetId = 0x5a00 + id.toInt(),
            emojis = emptyList(),
            snr = snr,
            rssi = rssi,
            hopsAway = hops,
            replyId = null,
        )
    }

    private fun node(
        num: Int,
        longName: String,
        shortName: String,
        hw: HardwareModel,
        role: Role,
        battery: Int,
        hops: Int,
        heardMinutesAgo: Int,
        dLat: Double,
        dLon: Double,
        snr: Float,
        rssi: Int,
        favorite: Boolean = false,
    ): Node = Node(
        num = num,
        user =
        User.Builder()
            .also {
                it.id = "!${num.toUInt().toString(16).padStart(8, '0')}"
                it.long_name = longName
                it.short_name = shortName
                it.hw_model = hw
                it.role = role
            }
            .build(),
        position =
        Position.Builder()
            .also {
                it.latitude_i = ((CENTER_LAT + dLat) * 1e7).toInt()
                it.longitude_i = ((CENTER_LON + dLon) * 1e7).toInt()
                it.altitude = 120 + (num and 0xff)
                it.sats_in_view = 8
                it.time = now - heardAgo(heardMinutesAgo)
            }
            .build(),
        lastHeard = now - heardAgo(heardMinutesAgo),
        channel = 0,
        snr = snr,
        rssi = rssi,
        deviceMetrics =
        DeviceMetrics.Builder()
            .also {
                it.battery_level = battery
                it.voltage = 3.6f + battery / 250f
                it.channel_utilization = 4.2f
                it.air_util_tx = 1.1f
                it.uptime_seconds = 86_400
            }
            .build(),
        hopsAway = hops,
        isFavorite = favorite,
    )
}

private typealias Role = Config.DeviceConfig.Role
