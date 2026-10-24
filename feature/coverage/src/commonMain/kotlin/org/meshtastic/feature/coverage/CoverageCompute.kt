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
package org.meshtastic.feature.coverage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Where a coverage estimate's CPU work runs: [Dispatchers.Default] with one worker held back. The mesh service shares
 * Default, and a sweep queues hundreds of radials, so an unlimited one would leave incoming radio data waiting on it.
 */
@Suppress("InjectDispatcher") // the sweep and tile decode are built outside DI
internal val coverageComputeDispatcher: CoroutineDispatcher by lazy {
    Dispatchers.Default.limitedParallelism(maxOf(1, processorCount() - 1))
}

/** CPU cores available to the process; common code has no API for it. */
internal expect fun processorCount(): Int
