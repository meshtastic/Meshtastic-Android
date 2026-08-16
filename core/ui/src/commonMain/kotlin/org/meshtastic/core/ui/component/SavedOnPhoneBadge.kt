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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_saved_on_phone
import org.meshtastic.core.resources.node_saved_on_phone_description
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PhoneAndroid
import org.meshtastic.core.ui.theme.AppTheme

/**
 * Labeled indicator for a node that is locally cached but wasn't part of the connected radio's current session snapshot
 * — the phone deliberately keeps rows the radio no longer reports (multi-radio use, meshes bigger than the radio's own
 * bounded NodeDB), so this explains why the row's live metrics may be absent or dated rather than letting it look
 * broken (#6263). See [NodeStatusIcons] for the icon-only variant used in dense node rows.
 */
@Composable
fun SavedOnPhoneBadge(modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.outline) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.PhoneAndroid,
        contentDescription = stringResource(Res.string.node_saved_on_phone_description),
        label = stringResource(Res.string.node_saved_on_phone),
        contentColor = contentColor,
    )
}

@PreviewLightDark
@Composable
private fun SavedOnPhoneBadgePreview() {
    AppTheme { SavedOnPhoneBadge() }
}
