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
package org.meshtastic.feature.intro

import org.meshtastic.core.ui.util.PermissionUiState

/**
 * Android [IntroPermissions], holding the hoisted states from [AppIntroductionScreen] verbatim.
 *
 * There is deliberately no adaptation layer here. An earlier version wrapped each [PermissionUiState] in a
 * granted/not-granted facade, which discarded the status the screens need to choose between re-requesting and routing
 * to app settings — and left the primary button inert once a permission was permanently denied.
 */
internal class AndroidIntroPermissions(
    override val bluetooth: PermissionUiState,
    override val location: PermissionUiState,
    override val notification: PermissionUiState?,
    override val bluetoothRequiresLocation: Boolean,
) : IntroPermissions
