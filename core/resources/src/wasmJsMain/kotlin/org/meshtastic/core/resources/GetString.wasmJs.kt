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
package org.meshtastic.core.resources

import org.jetbrains.compose.resources.StringResource

// A browser can't synchronously block its one event-loop thread, so there is no wasmJs runBlocking.
// Fail loudly rather than return a wrong value; callers on web must use getStringSuspend() instead.
actual fun getString(stringResource: StringResource): String =
    error("getString() is not supported on web -- use getStringSuspend() instead")

actual fun getString(stringResource: StringResource, vararg formatArgs: Any): String =
    error("getString() is not supported on web -- use getStringSuspend() instead")
