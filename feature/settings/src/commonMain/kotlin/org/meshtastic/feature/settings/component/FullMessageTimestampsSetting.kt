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
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.show_full_message_timestamps
import org.meshtastic.core.resources.show_full_message_timestamps_summary
import org.meshtastic.core.ui.component.SwitchPreference

@Composable
internal fun FullMessageTimestampsSetting(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SwitchPreference(
        title = stringResource(Res.string.show_full_message_timestamps),
        summary = stringResource(Res.string.show_full_message_timestamps_summary),
        checked = checked,
        enabled = true,
        onCheckedChange = onCheckedChange,
    )
}
