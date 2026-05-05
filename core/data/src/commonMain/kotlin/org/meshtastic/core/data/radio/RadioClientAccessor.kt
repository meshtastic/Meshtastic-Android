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
package org.meshtastic.core.data.radio

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.sdk.RadioClient

/**
 * Platform-agnostic accessor for the active [RadioClient] instance.
 *
 * Implemented by platform-specific providers (Android's `RadioClientProvider`, Desktop's
 * `DesktopRadioClientProvider`) that handle transport creation and lifecycle. The shared
 * [SdkRadioController] and [SdkStateBridge] depend on this interface rather than any
 * concrete provider.
 */
interface RadioClientAccessor {
    /** Active [RadioClient], or `null` when disconnected or between connections. */
    val client: StateFlow<RadioClient?>

    /** Tear down the existing client and rebuild + connect using the current saved address. */
    fun rebuildAndConnectAsync()

    /** Gracefully disconnect and release the active SDK radio client. */
    fun disconnect()
}
