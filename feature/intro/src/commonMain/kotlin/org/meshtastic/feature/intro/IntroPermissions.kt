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

import androidx.compose.runtime.staticCompositionLocalOf
import org.meshtastic.core.ui.util.PermissionUiState

/**
 * Aggregated permission states needed by the intro onboarding flow.
 *
 * Each entry is the full [PermissionUiState] — status, request action, and open-settings action — deliberately *not*
 * narrowed to a granted/not-granted boolean. The distinction is load-bearing: once a permission reaches
 * [org.meshtastic.core.ui.util.PermissionStatus.PERMANENTLY_DENIED] the system stops showing its dialog, so a screen
 * that only knows `isGranted` renders a primary button that silently does nothing when tapped. Carrying the status lets
 * each screen offer the recovery that will actually work.
 */
interface IntroPermissions {
    val bluetooth: PermissionUiState

    val location: PermissionUiState

    /** Null on platforms / API levels where notifications are not gated by a runtime permission (pre-Android 13). */
    val notification: PermissionUiState?

    /**
     * True where BLE scanning is gated by the location permission rather than the Android 12 "Nearby devices"
     * permissions (API < 31). The Bluetooth screen asks for a different permission on those devices, so it must say so
     * rather than naming a permission the user will never see.
     */
    val bluetoothRequiresLocation: Boolean
}

/** Provides platform-specific permission states to the intro nav graph. */
@Suppress("CompositionLocalAllowlist")
val LocalIntroPermissions = staticCompositionLocalOf<IntroPermissions> { error("IntroPermissions not provided") }
