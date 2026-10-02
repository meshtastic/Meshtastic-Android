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
package org.meshtastic.desktop

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import org.meshtastic.core.common.log.InMemoryLogBuffer

/**
 * Sends Kermit output to the console and to [InMemoryLogBuffer], which the Debug screen views and exports. Release
 * builds keep Info and above, as Android release does; debug builds keep every level.
 */
internal fun installDesktopLogging(isDebug: Boolean) {
    Logger.setMinSeverity(if (isDebug) Severity.Verbose else Severity.Info)
    Logger.setLogWriters(listOf(platformLogWriter(), InMemoryLogBuffer))
}
