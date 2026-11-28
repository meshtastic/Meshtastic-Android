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
package org.meshtastic.desktop.mcp

import com.squareup.wire.WireField
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig

/** Where `set_config` writes. */
internal sealed interface McpConfigDest {
    data object Local : McpConfigDest

    data class Remote(val num: Int) : McpConfigDest
}

/** [raw] as a `set_config` target. Absent or [myNum]'s own id is the local node, as the settings screens decide it. */
internal fun parseMcpConfigDest(raw: String?, myNum: Int): McpConfigDest {
    if (raw == null) return McpConfigDest.Local
    val num = parseMcpNodeNum(raw) ?: throw McpFieldException("dest must be a node id like !1a2b3c4d")
    return if (num == myNum) McpConfigDest.Local else McpConfigDest.Remote(num)
}

/** The kind of [section], or null when no section has that name. */
internal fun mcpSectionKind(section: String): McpSectionKind? = when {
    section == OWNER_SECTION -> McpSectionKind.OWNER
    sectionOf(LocalConfig.Builder().build(), section) != null -> McpSectionKind.CONFIG
    sectionOf(LocalModuleConfig.Builder().build(), section) != null -> McpSectionKind.MODULE
    else -> null
}

/**
 * The `AdminMessage.ConfigType` or `ModuleConfigType` value that reads [section] of [kind]. Each type is its section's
 * `Config` or `ModuleConfig` oneof tag minus one.
 */
internal fun mcpSectionType(kind: McpSectionKind, section: String): Int {
    val holder = if (kind == McpSectionKind.CONFIG) Config::class.java else ModuleConfig::class.java
    return holder.getField(section).getAnnotation(WireField::class.java).tag - 1
}

internal fun Int.toMcpNodeId(): String = NodeAddress.numToDefaultId(this)
