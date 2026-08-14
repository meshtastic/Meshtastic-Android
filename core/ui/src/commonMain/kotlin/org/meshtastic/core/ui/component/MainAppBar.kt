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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
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
import org.meshtastic.core.ui.theme.LocalEventTheme

/** Alpha for the ambient event accent wash over the app bar — subtle enough to keep title text legible. */
private const val EVENT_ACCENT_ALPHA = 0.12f

/** Height of the event brand rule under the app bar. A hairline: brand presence without stealing vertical space. */
private val EVENT_BRAND_RULE_HEIGHT = 3.dp

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
    onClickChip: (Node) -> Unit,
    // Trailing slot: a @Composable content lambda, not an event handler (detekt LambdaParameterEventTrailing).
    actions: @Composable () -> Unit,
) {
    // Ambient event theming: when connected to event firmware (and not opted out), tint the bar with a faint wash of
    // the edition's accent color. Gated with the app-wide fonts via LocalEventTheme / the "Use event theme" toggle.
    val eventTheme = LocalEventTheme.current
    val accent = eventTheme?.accent
    val colors =
        if (accent != null) {
            TopAppBarDefaults.topAppBarColors(
                containerColor =
                accent.copy(alpha = EVENT_ACCENT_ALPHA).compositeOver(MaterialTheme.colorScheme.surface),
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        }
    Column(modifier = modifier) {
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
                // The Meshtastic logo is never swapped for event branding — the app's identity stays put. Event
                // firmware is surfaced on the Connections screen instead (EventFirmwareCard).
                { Icon(imageVector = vectorResource(Res.drawable.ic_meshtastic), contentDescription = null) }
            },
            actions = {
                TopBarActions(
                    ourNode = ourNode,
                    showNodeChip = showNodeChip,
                    actions = actions,
                    onClickChip = onClickChip,
                )
            },
        )
        EventPaletteStrip(palette = eventTheme?.palette.orEmpty(), height = EVENT_BRAND_RULE_HEIGHT)
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
