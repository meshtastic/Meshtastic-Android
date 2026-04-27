/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.wear.presentation.model

data class MockNode(
    val name: String,
    val shortName: String,
    val online: Boolean,
    val battery: Int?,
    val snr: Float?,
    val lastSeen: String,
    val favourite: Boolean = false,
)

val mockNodes =
    listOf(
        MockNode("Coltin's Node", "CLT", true, 87, 4.2f, "now", favourite = true),
        MockNode("Holden's Node", "HLD", true, 43, 1.8f, "now"),
        MockNode("Base Station", "BASE", true, null, 6.5f, "now"),
        MockNode("Mountain Relay", "MTN", false, 12, null, "3h ago"),
        MockNode("Park Node", "PRK", false, null, null, "1d ago"),
    )

data class MockMessage(
    val text: String,
    val sender: String,
    val isMe: Boolean,
    val time: String,
    val delivered: Boolean = true,
)

val mockMessages =
    listOf(
        MockMessage("Hey! You on the mesh?", "Holden", false, "14:22"),
        MockMessage("Yeah, just got my Pixel Watch set up!", "You", true, "14:23"),
        MockMessage("Nice! How's signal?", "Holden", false, "14:24"),
        MockMessage("SNR ~4, pretty solid", "You", true, "14:24"),
    )

data class MockChannel(val name: String, val lastMsg: String, val unread: Int = 0)

val mockChannels =
    listOf(
        MockChannel("Public", "Base Station: all clear", unread = 2),
        MockChannel("Hiking Group", "Holden: at the summit", unread = 1),
        MockChannel("Event Net", "No recent messages"),
    )

data class MockDM(
    val name: String,
    val shortName: String,
    val lastMsg: String,
    val online: Boolean,
    val unread: Int = 0,
)

val mockDMs =
    listOf(
        MockDM("Holden Anderson", "HLD", "You: heading back now", true, unread = 0),
        MockDM("Base Station", "BSE", "Signal check complete", true, unread = 1),
        MockDM("Mountain Relay", "MTN", "Last seen 3h ago", false, unread = 0),
        MockDM("Park Node", "PRK", "Last seen yesterday", false, unread = 0),
    )
