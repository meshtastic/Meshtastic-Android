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
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_saved_on_phone
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PhoneAndroid

/** Labels a retained node that was absent from the current connection's completed radio NodeDB download. */
@Composable
internal fun SavedOnPhoneInfo(modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.node_saved_on_phone)
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.PhoneAndroid,
        contentDescription = label,
        text = label,
        contentColor = MaterialTheme.colorScheme.outline,
    )
}
