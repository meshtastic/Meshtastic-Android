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
package org.meshtastic.core.service

import android.app.Application
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.MeshtasticUri
import org.meshtastic.core.common.util.toAndroidUri
import org.meshtastic.core.repository.FileService

@Single
class AndroidFileService(private val context: Application) : FileService {
    override suspend fun write(uri: MeshtasticUri, block: suspend (BufferedSink) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri.toAndroidUri(), "wt")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).sink().buffer().use { sink ->
                        block(sink)
                    }
                }
                true
            } catch (e: Exception) {
                Logger.e(e) { "Failed to write to URI: $uri" }
                false
            }
        }

    override suspend fun read(uri: MeshtasticUri, block: suspend (BufferedSource) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri.toAndroidUri())?.use { inputStream ->
                    inputStream.source().buffer().use { source ->
                        block(source)
                    }
                }
                true
            } catch (e: Exception) {
                Logger.e(e) { "Failed to read from URI: $uri" }
                false
            }
        }
}
