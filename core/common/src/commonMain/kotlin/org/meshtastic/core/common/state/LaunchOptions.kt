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
package org.meshtastic.core.common.state

import org.koin.core.annotation.Single

/**
 * Switches the platform entry point sets from its own launch arguments, for automation such as the store-screenshot
 * pipeline. Only the entry point writes them, and only in a debug build; a deep link can never set one, because the
 * trust checks they relax exist to stop links.
 */
@Single
class LaunchOptions {
    /** Apply a `connections` deep link's address without asking the user to confirm it. */
    var skipDeepLinkConfirmation: Boolean = false
}
