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
package org.meshtastic.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_meshtastic
import org.meshtastic.core.resources.navigate_back
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.EventBrandingIcon
import org.meshtastic.core.ui.util.LocalEventBranding
import org.meshtastic.core.ui.util.accentColorOrNull

/** Alpha for the ambient event accent wash over the app bar — subtle enough to keep title text legible. */
private const val EVENT_ACCENT_ALPHA = 0.12f

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    ourNode: Node?,
    showNodeChip: Boolean,
    canNavigateUp: Boolean,
    onNavigateUp: () -> Unit,
    actions: @Composable () -> Unit,
    onClickChip: (Node) -> Unit,
    brandingContent: @Composable () -> Unit = { EventAwareBranding() },
) {
    // Ambient event theming: when connected to event firmware, tint the bar with a faint wash of its accent color.
    val accent = LocalEventBranding.current?.accentColorOrNull()
    val colors =
        if (accent != null) {
            TopAppBarDefaults.topAppBarColors(
                containerColor =
                accent.copy(alpha = EVENT_ACCENT_ALPHA).compositeOver(MaterialTheme.colorScheme.surface),
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        }
    TopAppBar(
        colors = colors,
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        subtitle = {
            subtitle?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
        navigationIcon =
        if (canNavigateUp) {
            {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = MeshtasticIcons.ArrowBack,
                        contentDescription = stringResource(Res.string.navigate_back),
                    )
                }
            }
        } else {
            { brandingContent() }
        },
        actions = {
            TopBarActions(ourNode = ourNode, showNodeChip = showNodeChip, actions = actions, onClickChip = onClickChip)
        },
    )
}

/** Reads [LocalEventBranding] to show event branding (tap → [EventInfoSheet]), or the default Meshtastic logo. */
@Composable
private fun EventAwareBranding() {
    val eventEdition = LocalEventBranding.current
    if (eventEdition == null) {
        Icon(imageVector = vectorResource(Res.drawable.ic_meshtastic), contentDescription = null)
        return
    }
    // Every event edition is tappable for its info sheet. The icon prefers the hosted iconUrl, then a bundled
    // drawable, then the Meshtastic logo — see EventBrandingIcon.
    var showSheet by remember { mutableStateOf(false) }
    val brandingModifier = Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { showSheet = true }
    EventBrandingIcon(edition = eventEdition, modifier = brandingModifier)
    if (showSheet) {
        EventInfoSheet(edition = eventEdition, onDismiss = { showSheet = false })
    }
}

@Composable
private fun TopBarActions(
    ourNode: Node?,
    showNodeChip: Boolean,
    actions: @Composable () -> Unit,
    onClickChip: (Node) -> Unit,
) {
    AnimatedVisibility(visible = showNodeChip, enter = fadeIn(), exit = fadeOut()) {
        ourNode?.let { node ->
            NodeChip(modifier = Modifier.padding(horizontal = 16.dp), node = node, onClick = onClickChip)
        }
    }

    actions()
}
