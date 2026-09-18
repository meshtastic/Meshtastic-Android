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
import org.meshtastic.core.common.util.nowMillis
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
import org.meshtastic.proto.User

/**
 * One plausible mesh shared by every store screenshot, so the five shots tell one story: a base camp, a ridge-top
 * router, and a handful of hikers, boats and trucks spread across a valley outside Dallas. Nine nodes, so the map never
 * clusters them. "Last heard" and message timestamps hang off the wall clock because the row composables format them
 * relative to now.
 */
internal object SampleMesh {
    const val CENTER_LAT = 32.7767
    const val CENTER_LON = -96.797

    /** Seconds since the epoch, taken once so every node's "last heard" reads as minutes or hours, not years. */
    private val now: Int = (nowMillis / 1_000L).toInt()

    /** A fixed, obviously synthetic 256-bit key so the detail page shows a keyed node rather than a key warning. */
    private val ridgeKey = ByteArray(32) { i -> (i * 13 + 29).toByte() }.toByteString()

    val baseCamp =
        node(
            0x2b3c4d5e,
            "Base Camp",
            "BASE",
            HardwareModel.HELTEC_V3,
            Role.CLIENT,
            95,
            0,
            5,
            0.0,
            0.0,
            0f,
            0,
            favorite = true,
        )
    val ridgeRepeater =
        node(
            0x1a2b3c4d,
            "Ridge Repeater",
            "RDGE",
            HardwareModel.RAK4631,
            Role.ROUTER,
            88,
            0,
            160,
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
        node(0x3c4d5e6f, "Trailhead", "TRLH", HardwareModel.TBEAM, Role.CLIENT, 72, 1, 180, -0.03, 0.045, 8.25f, -92)
    val riverCrossing =
        node(
            0x4d5e6f70,
            "River Crossing",
            "RIVR",
            HardwareModel.T_ECHO,
            Role.CLIENT_MUTE,
            64,
            1,
            420,
            -0.045,
            -0.02,
            6.0f,
            -101,
        )
    val summitSolar =
        node(
            0x5e6f7081,
            "Summit Solar",
            "SMMT",
            HardwareModel.RAK4631,
            Role.ROUTER,
            91,
            2,
            900,
            0.05,
            -0.04,
            3.5f,
            -110,
            favorite = true,
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
            1500,
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
            3600,
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
            240,
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
            660,
            -0.02,
            -0.035,
            4.5f,
            -104,
        )

    /** The node list, in the order the real list would show it: us first, then favorites, then by last heard. */
    val nodes: List<Node> =
        listOf(
            baseCamp,
            ridgeRepeater,
            summitSolar,
            trailhead,
            sarahsTruck,
            riverCrossing,
            hamShack,
            kayakDan,
            fireLookout,
        )

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

    /** The LongTurbo thread as seen from Base Camp: a day trip checking in from the ridge and the river. */
    val messages: List<Message> =
        listOf(
            received(trailhead, "Heading up from the trailhead now, 4 of us", "09:02", 39, 5.5f, -92, hops = 1),
            received(sarahsTruck, "Parked at the overflow lot, radio on", "09:05", 36, 9.0f, -85, hops = 1),
            sent("Copy. Weather window closes around 2, keep moving", "09:07", 34),
            received(trailhead, "Made the ridge, 2 bars on the repeater", "09:31", 10, 11.25f, -70, hops = 1)
                .withReactions(reaction(baseCamp, "👍", 9), reaction(sarahsTruck, "🔥", 8)),
            received(kayakDan, "Water at the crossing is low, safe to ford", "09:34", 7, 2.75f, -113, hops = 2),
            sent("Great, we'll take the crossing route back", "09:35", 6),
            received(sarahsTruck, "Bringing the truck around to the lower lot at 3", "09:38", 3, 8.5f, -88, hops = 1)
                .withReactions(reaction(trailhead, "❤️", 2)),
            received(trailhead, "Lunch at the lookout, back on the air in 30", "09:41", 0, 10.0f, -74, hops = 1),
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
        text: String,
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

    private fun sent(text: String, time: String, minutesAgo: Int): Message = message(
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
        text: String,
        time: String,
        minutesAgo: Int,
        fromLocal: Boolean,
        status: MessageStatus,
        snr: Float?,
        rssi: Int?,
        hops: Int,
    ): Message {
        val id = ++nextMessageId
        return Message(
            uuid = id,
            receivedTime = nowMillis - minutesAgo * 60_000L,
            node = from,
            text = text,
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
        heardAgoSec: Int,
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
                it.time = now - heardAgoSec
            }
            .build(),
        lastHeard = now - heardAgoSec,
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
