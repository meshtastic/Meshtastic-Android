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
package org.meshtastic.screenshot.marketing

/** The banner drawn above the phone: a headline and one line of body copy. */
internal data class Caption(val title: String, val description: String)

/** The five listing shots in listing order; file names sort into that order on Play and F-Droid alike. */
internal enum class Shot(val fileName: String) {
    Messages("1_messages"),
    Nodes("2_nodes"),
    Map("3_map"),
    NodeDetail("4_node_detail"),
    Channels("5_channels"),
}

/** Store copy per locale. Only en-US is written today; a new locale is a new entry here plus `-PmarketingLocales`. */
internal object Captions {
    private val enUs =
        mapOf(
            Shot.Messages to
                Caption(
                    "Text without cell service",
                    "Messages hop node to node over LoRa. No towers, no SIM, no subscription.",
                ),
            Shot.Nodes to
                Caption("See everyone on your mesh", "Battery, signal, distance and hops for every node you can hear."),
            Shot.Map to Caption("Every node on the map", "Positions shared over the mesh, with no internet required."),
            Shot.NodeDetail to
                Caption("Every node in detail", "Signal, battery, position, hardware and firmware at a glance."),
            Shot.Channels to
                Caption(
                    "Share a channel with a QR code",
                    "Set up a private encrypted mesh with your group in seconds.",
                ),
        )

    private val byLocale: Map<String, Map<Shot, Caption>> = mapOf("en-US" to enUs)

    fun forLocale(locale: String): Map<Shot, Caption> =
        requireNotNull(byLocale[locale]) { "No captions for locale '$locale'; known: ${byLocale.keys}" }
}
