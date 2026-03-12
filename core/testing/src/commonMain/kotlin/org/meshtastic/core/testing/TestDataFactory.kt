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
     * @return A Node instance with provided or default values
     */
    fun createTestNode(
        num: Int = 1,
        userId: String = "!test0001",
        longName: String = "Test User",
        shortName: String = "T",
    ): Node {
        val user = User(id = userId, long_name = longName, short_name = shortName)
        return Node(num = num, user = user, lastHeard = 0, snr = 0f, rssi = 0, channel = 0)
    }

    /**
     * Creates multiple test nodes with sequential IDs.
     *
     * @param count Number of nodes to create
     * @param baseNum Starting node number (default: 1)
     * @return A list of Node instances
     */
    fun createTestNodes(count: Int, baseNum: Int = 1): List<Node> = (0 until count).map { i ->
        createTestNode(
            num = baseNum + i,
            userId = "!test${(baseNum + i).toString().padStart(4, '0')}",
            longName = "Test User $i",
            shortName = "T$i",
        )
    }
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
