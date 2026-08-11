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
package org.meshtastic.app.map

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.app.map.model.NodeClusterItem
import org.meshtastic.core.model.Node
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapNodeClusterItemsTest {

    @Test
    fun `camera-only recompositions reuse two thousand cluster items`() = runComposeUiTest {
        val nodes = testNodes(count = NODE_COUNT)
        var cameraFrame by mutableIntStateOf(0)
        val observedLists = mutableListOf<List<NodeClusterItem>>()

        setContent {
            val filteredNodes = nodes.filter { cameraFrame >= 0 }
            val items =
                rememberNodeClusterItems(
                    nodes = filteredNodes,
                    myNodeNum = null,
                    relativeTimeBucket = FIXED_TIME_BUCKET,
                )
            SideEffect { observedLists += items }
        }
        waitForIdle()

        repeat(SIMULATED_CAMERA_FRAMES) {
            runOnIdle { cameraFrame += 1 }
            waitForIdle()
        }

        assertEquals(SIMULATED_CAMERA_FRAMES + 1, observedLists.size)
        val first = observedLists.first()
        assertEquals(NODE_COUNT, first.size)
        observedLists.drop(1).forEach { items ->
            assertTrue(first === items, "camera-only changes must retain the same 2,000-item list")
        }
    }

    @Test
    fun `node changes and minute rollover refresh cluster items`() = runComposeUiTest {
        var nodes by mutableStateOf(testNodes(count = 1))
        var relativeTimeBucket by mutableLongStateOf(FIXED_TIME_BUCKET)
        var latestItems: List<NodeClusterItem> = emptyList()

        setContent {
            val items =
                rememberNodeClusterItems(nodes = nodes, myNodeNum = null, relativeTimeBucket = relativeTimeBucket)
            SideEffect { latestItems = items }
        }
        waitForIdle()
        val initialItems = latestItems

        runOnIdle {
            val node = nodes.single()
            nodes = listOf(node.copy(user = node.user.copy(short_name = "NEW")))
        }
        waitForIdle()
        val changedNodeItems = latestItems
        assertNotSame(initialItems, changedNodeItems)
        assertTrue(changedNodeItems.single().title.startsWith("NEW "))

        runOnIdle { relativeTimeBucket += 1 }
        waitForIdle()
        assertNotSame(changedNodeItems, latestItems)
    }

    private fun testNodes(count: Int): List<Node> = List(count) { index ->
        Node(
            num = index + 1,
            user = User(id = "!${index + 1}", long_name = "Node ${index + 1}", short_name = "N$index"),
            position =
            Position(
                latitude_i = 210_000_000 + index,
                longitude_i = -1_570_000_000 + index,
                time = 1_700_000_000,
            ),
        )
    }

    private companion object {
        const val NODE_COUNT = 2_000
        const val SIMULATED_CAMERA_FRAMES = 30
        const val FIXED_TIME_BUCKET = 123L
    }
}
