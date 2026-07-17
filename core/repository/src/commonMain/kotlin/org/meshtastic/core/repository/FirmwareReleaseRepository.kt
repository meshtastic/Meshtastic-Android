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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.FirmwareRelease

interface FirmwareReleaseRepository {
    /** A flow that provides the latest STABLE firmware release. */
    val stableRelease: Flow<FirmwareRelease?>

    /** A flow that provides the latest ALPHA firmware release. */
    val alphaRelease: Flow<FirmwareRelease?>

    /**
     * A flow that provides the current NIGHTLY preview build, or null when none is published. Sourced from
     * meshtastic.github.io rather than the API server, and surfaced only behind the hidden-features unlock.
     */
    val nightlyRelease: Flow<FirmwareRelease?>

    /**
     * Fetches the authoritative firmware targets from a release's manifest URL. Returns null when the manifest cannot
     * be retrieved or parsed, so callers can fail closed rather than offering an incompatible firmware update.
     */
    suspend fun getManifestTargets(release: FirmwareRelease): Set<String>?

    /** Invalidates the local cache of firmware releases. */
    suspend fun invalidateCache()
}
