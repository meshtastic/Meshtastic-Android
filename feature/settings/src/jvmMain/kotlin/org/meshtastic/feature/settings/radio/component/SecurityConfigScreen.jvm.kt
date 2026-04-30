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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.runtime.Composable
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.Config

@Composable
actual fun ExportSecurityConfigButton(
    viewModel: RadioConfigViewModel,
    enabled: Boolean,
    securityConfig: Config.SecurityConfig,
) {
    // Desktop currently does not implement a specific "export security config" button
    // within the config screen. If it did, we'd add it here.
}
