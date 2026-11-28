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
package org.meshtastic.core.ui.label

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getPluralStringSuspend
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.label_print_adds_channels
import org.meshtastic.core.resources.label_print_replaces_channels
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals

/** design#185: a channel label says whether scanning adds the channels or replaces the scanner's. */
@OptIn(ExperimentalTestApi::class)
class LabelSpecsTest {
    private val twoChannels =
        ChannelSet.Builder()
            .also {
                it.settings =
                    listOf("Hiking", "Base").map { name ->
                        ChannelSettings.Builder().also { s -> s.name = name }.build()
                    }
            }
            .build()

    @Test
    fun `a channel label says it adds its channels or replaces the scanner's as Share Channels chose`() =
        runComposeUiTest {
            val actions = mutableMapOf<Boolean, String>()
            setContent {
                for (shouldAdd in listOf(true, false)) {
                    val spec = channelLabelSpec(twoChannels, "https://meshtastic.org/e/#x", shouldAdd, "LongFast")
                    actions[shouldAdd] = (spec as LabelSpec.Channels).content.action
                }
            }
            waitForIdle()

            assertEquals(
                mapOf(
                    true to getPluralStringSuspend(Res.plurals.label_print_adds_channels, 2, 2),
                    false to getString(Res.string.label_print_replaces_channels),
                ),
                actions.toMap(),
            )
        }

    // A node whose user has no ID yet is named by its number, the !%08x form the firmware gives node IDs.
    @Test
    fun `a name tag for a node without a user ID prints the ID made from its number`() = runComposeUiTest {
        var nodeId: String? = null
        val node = Node(num = 0xDEADBEEF.toInt(), user = User.Builder().also { it.long_name = "Wanderer" }.build())
        setContent { nodeId = (nameTagSpec(node, SharedContact.Builder().build()) as LabelSpec.NameTag).content.nodeId }
        waitForIdle()

        assertEquals("!deadbeef", nodeId)
    }
}
