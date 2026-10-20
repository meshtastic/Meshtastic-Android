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

import org.meshtastic.core.model.util.ONLINE_WINDOW_SECONDS
import org.meshtastic.proto.Config

/**
 * The node-level map filters, shared by every view that draws nodes over a place: both map renderers and the camera
 * mesh view. [lastHeardSeconds] of 0 means no limit.
 */
data class NodeMapFilter(
    val onlyFavorites: Boolean = false,
    val showIgnored: Boolean = false,
    /** Roles the user has switched off. Excluded rather than included so a role added by future firmware shows. */
    val excludedRoles: Set<Config.DeviceConfig.Role> = emptySet(),
    val includeUnknown: Boolean = true,
    val onlyOnline: Boolean = false,
    val onlyDirect: Boolean = false,
    val excludeMqtt: Boolean = false,
    val lastHeardSeconds: Long = 0,
    val onlySigned: Boolean = false,
    val onlyEncrypted: Boolean = false,
)

/**
 * One node against every filter.
 *
 * The node-level rules match the node list's query exactly, because they carry its labels: direct means `hopsAway == 0`
 * (a node whose distance was never measured, -1, is not direct) and unknown means a node that has not sent a short
 * name.
 *
 * [NodeMapFilter.showIgnored] is the one place the map deliberately differs. The node list segregates (`isIgnored ==
 * showIgnored`, so switching it on shows *only* ignored nodes); here it reads literally and adds them, because a map of
 * nothing but ignored nodes is not a view anyone asked for.
 */
fun Node.passesMapFilter(filter: NodeMapFilter, nowSeconds: Long): Boolean {
    val secondsSinceHeard = nowSeconds - lastHeard
    return passesIdentityFilters(filter) &&
        passesSecurityFilters(filter) &&
        passesReachabilityFilters(filter, secondsSinceHeard)
}

/**
 * Resolves persisted role names back to roles, dropping any the current protobufs no longer define.
 *
 * Names rather than ordinals, and forgiving of names it does not know: a set written by a newer build (or a role
 * removed from the protos) must not throw on the way back in.
 */
fun decodeExcludedRoles(names: Set<String>): Set<Config.DeviceConfig.Role> =
    names.mapNotNullTo(mutableSetOf()) { name -> Config.DeviceConfig.Role.entries.firstOrNull { it.name == name } }

/** Who the node is: favorited, ignored, its role, and whether its name has arrived yet. */
private fun Node.passesIdentityFilters(filter: NodeMapFilter): Boolean = (!filter.onlyFavorites || isFavorite) &&
    (!isIgnored || filter.showIgnored) &&
    user.role !in filter.excludedRoles &&
    (filter.includeUnknown || user.short_name.isNotEmpty())

/** How the node is reachable: online, hop count, bearer, and how recently it was heard. */
private fun Node.passesReachabilityFilters(filter: NodeMapFilter, secondsSinceHeard: Long): Boolean =
    (!filter.onlyOnline || secondsSinceHeard <= ONLINE_WINDOW_SECONDS) &&
        (!filter.onlyDirect || (hopsAway == 0 && !viaMqtt)) &&
        (!filter.excludeMqtt || !viaMqtt) &&
        (filter.lastHeardSeconds == 0L || secondsSinceHeard <= filter.lastHeardSeconds)

/**
 * The two security filters (design#149).
 *
 * Signing reads the raw flag rather than the version-gated indicator: the filter answers "has the radio verified this
 * node's signature", which is what the badge beside it claims. Encrypted excludes a stored key that stopped matching,
 * since a mismatch is not a key you can safely encrypt to.
 */
private fun Node.passesSecurityFilters(filter: NodeMapFilter): Boolean =
    (!filter.onlySigned || signsPackets) && (!filter.onlyEncrypted || (hasPKC && !mismatchKey))
