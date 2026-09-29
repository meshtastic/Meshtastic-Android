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
package org.meshtastic.feature.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.remotely_administrating
import org.meshtastic.core.ui.component.MainAppBar

/**
 * App bar for a screen that configures either the connected node or a remote one: while [isLocal] the subtitle is
 * [localSubtitle], otherwise it names [destNode] as the node being administered.
 */
@Composable
internal fun RadioAdminAppBar(
    title: String,
    isLocal: Boolean,
    destNode: Node?,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    localSubtitle: String? = destNode?.user?.long_name,
    ourNode: Node? = null,
    onClickChip: (Node) -> Unit = {},
    showNodeChip: Boolean = false,
    canNavigateUp: Boolean = true,
) {
    MainAppBar(
        modifier = modifier,
        title = title,
        subtitle =
        if (isLocal) {
            localSubtitle
        } else {
            stringResource(Res.string.remotely_administrating, destNode?.user?.long_name.orEmpty())
        },
        ourNode = ourNode,
        showNodeChip = showNodeChip,
        canNavigateUp = canNavigateUp,
        onNavigateUp = onNavigateUp,
        onClickChip = onClickChip,
        actions = {},
    )
}
