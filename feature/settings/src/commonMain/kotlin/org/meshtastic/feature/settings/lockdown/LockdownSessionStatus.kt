/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.lockdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.service.LockdownTokenInfo

/**
 * Displays lockdown session token status: remaining boots and expiry information.
 * Visible only when the session is unlocked and token info is available.
 */
@Composable
fun LockdownSessionStatus(
    tokenInfo: LockdownTokenInfo?,
    modifier: Modifier = Modifier,
) {
    if (tokenInfo == null) return

    Column(modifier = modifier.padding(horizontal = PADDING_DP.dp, vertical = PADDING_VERTICAL_DP.dp)) {
        Text(
            text = "Session: ${tokenInfo.bootsRemaining} reboots remaining",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tokenInfo.expiryEpoch > 0L) {
            Text(
                text = "Expires at epoch ${tokenInfo.expiryEpoch}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "No time limit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PADDING_DP = 8
private const val PADDING_VERTICAL_DP = 4
