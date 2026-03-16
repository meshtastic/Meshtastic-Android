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

import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.core.common.util.MeshtasticUri

/**
 * Abstracts file system operations (like reading from or writing to URIs) so that ViewModels can remain
 * platform-independent.
 */
interface FileService {
    /**
     * Opens a file or URI for writing and provides a [BufferedSink]. The sink is automatically closed after [block]
     * execution. Returns true if successful, false otherwise.
     */
    suspend fun write(uri: MeshtasticUri, block: suspend (BufferedSink) -> Unit): Boolean

    /**
     * Opens a file or URI for reading and provides a [BufferedSource]. The source is automatically closed after [block]
     * execution. Returns true if successful, false otherwise.
     */
    suspend fun read(uri: MeshtasticUri, block: suspend (BufferedSource) -> Unit): Boolean
}
