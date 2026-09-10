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
package org.meshtastic.core.repository

/**
 * The stored node-list filters, as one value.
 *
 * The defaults here are what a fresh install reads back, so they are the app's real defaults, not an "unset"
 * placeholder. Adding a filter is one property here plus one key in the store.
 */
data class NodeFilterPrefs(
    /** Show nodes whose user info has not arrived yet. On by default: hiding them looks like packet loss. */
    val includeUnknown: Boolean = true,
    /** Hide routers, repeaters and other nodes that are infrastructure rather than people. */
    val excludeInfrastructure: Boolean = false,
    val onlyOnline: Boolean = false,
    /** Show only nodes heard with no intervening hop. */
    val onlyDirect: Boolean = false,
    val showIgnored: Boolean = false,
    val excludeMqtt: Boolean = false,
    /** Hide nodes not heard since the radio's current LoRa config took effect. */
    val excludeUnheard: Boolean = false,
)

/**
 * The stored map filters, as one value. The map keeps its own copy of the node-list vocabulary on purpose: filtering
 * the map to routers is not a statement about the contact list.
 */
data class MapFilterPrefs(
    val onlyFavorites: Boolean = false,
    val showWaypoints: Boolean = true,
    val showPrecisionCircle: Boolean = true,
    val onlyOnline: Boolean = false,
    val onlyDirect: Boolean = false,
    val excludeMqtt: Boolean = false,
    val showIgnored: Boolean = false,
    val includeUnknown: Boolean = true,
    /** Seconds; `0` means no last-heard limit. */
    val lastHeardSeconds: Long = 0,
    /** Seconds; `0` means no limit on how far back a node's track is drawn. */
    val lastHeardTrackSeconds: Long = 0,
    /**
     * Names of the device roles switched off.
     *
     * Excluded rather than included, and by name rather than ordinal: an included set would make nodes reporting a role
     * added by future firmware invisible with no way to discover why, and `ROUTER_CLIENT = 3` is already a deprecated
     * slot.
     */
    val excludedRoles: Set<String> = emptySet(),
)
