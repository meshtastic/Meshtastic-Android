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
package org.meshtastic.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_delete_fill0
import org.meshtastic.core.resources.ic_do_not_disturb_on
import org.meshtastic.core.resources.ic_nodes
import org.meshtastic.core.resources.ic_notes

val MeshtasticIcons.Notes: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_notes)
val MeshtasticIcons.DoDisturb: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_do_not_disturb_on)
val MeshtasticIcons.DeleteNode: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_delete_fill0)
val MeshtasticIcons.Nodes: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_nodes)
