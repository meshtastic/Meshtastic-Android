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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.proto.User

/**
 * Factory for creating test domain objects.
 *
 * Provides sensible defaults that can be overridden for specific test needs.
 */
@Suppress("MagicNumber") // test data padding
object TestDataFactory {

    /**
     * Creates a test [Node] with default values.
     *
     * @param num Node number (default: 1)
     * @param userId User ID in hex format (default: "!test0001")
     * @param longName User long name (default: "Test User")
     * @param shortName User short name (default: "T")
     * @param lastHeard Last heard timestamp in seconds (default: 0)
     * @param hwModel Hardware model (default: UNSET)
     * @return A Node instance with provided or default values
     */
    fun createTestNode(
        num: Int = 1,
        userId: String = "!test0001",
        longName: String = "Test User",
        shortName: String = "T",
        lastHeard: Int = 0,
        hwModel: org.meshtastic.proto.HardwareModel = org.meshtastic.proto.HardwareModel.UNSET,
        batteryLevel: Int? = 100,
    ): Node {
        val user = User(id = userId, long_name = longName, short_name = shortName, hw_model = hwModel)
        val metrics = org.meshtastic.proto.DeviceMetrics(battery_level = batteryLevel)
        return Node(num = num, user = user, lastHeard = lastHeard, snr = 0f, rssi = 0, channel = 0, deviceMetrics = metrics)
    }

    /**
     * Creates a test [org.meshtastic.proto.MeshPacket] with default values.
     */
    fun createTestPacket(
        from: Int = 1,
        to: Int = 0xffffffff.toInt(),
        decoded: org.meshtastic.proto.Data? = null,
        relayNode: Int = 0,
    ) = org.meshtastic.proto.MeshPacket(
        from = from,
        to = to,
        decoded = decoded,
        relay_node = relayNode,
    )

    /**
     * Creates multiple test nodes with sequential IDs.
     */
    fun createTestNodes(count: Int, baseNum: Int = 1): List<Node> = (0 until count).map { i ->
        createTestNode(
            num = baseNum + i,
            userId = "!test${(baseNum + i).toString().padStart(4, '0')}",
            longName = "Test User $i",
            shortName = "T$i",
        )
    }

    /** Creates a test [MyNodeInfo] with default values. */
    fun createMyNodeInfo(
        myNodeNum: Int = 1,
        hasGPS: Boolean = false,
        model: String? = "TBEAM",
        firmwareVersion: String? = "2.5.0",
        hasWifi: Boolean = false,
        pioEnv: String? = null,
    ) = MyNodeInfo(
        myNodeNum = myNodeNum,
        hasGPS = hasGPS,
        model = model,
        firmwareVersion = firmwareVersion,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 1L,
        messageTimeoutMsec = 300000,
        minAppVersion = 1,
        maxChannels = 8,
        hasWifi = hasWifi,
        channelUtilization = 0f,
        airUtilTx = 0f,
        deviceId = "!$myNodeNum",
        pioEnv = pioEnv,
    )
}

/**
 * Collects all emissions from a Flow into a list.
 *
 * Useful for asserting on Flow values in tests.
 *
 * Example:
 * ```kotlin
 * val values = flow { emit(1); emit(2) }.toList()
 * assertEquals(listOf(1, 2), values)
 * ```
 */
suspend inline fun <T> Flow<T>.toList(): List<T> {
    val result = mutableListOf<T>()
    collect { result.add(it) }
    return result
}
