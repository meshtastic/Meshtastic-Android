/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app.node

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.toDate
import org.meshtastic.core.common.util.toInstant
import org.meshtastic.core.data.repository.TracerouteSnapshotRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.feature.node.metrics.MetricsViewModel
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

@KoinViewModel
class AndroidMetricsViewModel(
    savedStateHandle: SavedStateHandle,
    private val app: Application,
    dispatchers: CoroutineDispatchers,
    meshLogRepository: MeshLogRepository,
    serviceRepository: ServiceRepository,
    nodeRepository: NodeRepository,
    tracerouteSnapshotRepository: TracerouteSnapshotRepository,
    nodeRequestActions: NodeRequestActions,
    alertManager: AlertManager,
    getNodeDetailsUseCase: GetNodeDetailsUseCase,
) : MetricsViewModel(
    savedStateHandle.get<Int>("destNum") ?: 0,
    dispatchers,
    meshLogRepository,
    serviceRepository,
    nodeRepository,
    tracerouteSnapshotRepository,
    nodeRequestActions,
    alertManager,
    getNodeDetailsUseCase,
) {
    override fun savePositionCSV(uri: Any) {
        if (uri is Uri) {
            savePositionCSVAndroid(uri)
        }
    }

    private fun savePositionCSVAndroid(uri: Uri) = viewModelScope.launch(dispatchers.main) {
        val positions = state.value.positionLogs
        writeToUri(uri) { writer ->
            writer.appendLine(
                "\"date\",\"time\",\"latitude\",\"longitude\",\"altitude\",\"satsInView\",\"speed\",\"heading\"",
            )

            val dateFormat = SimpleDateFormat("\"yyyy-MM-dd\",\"HH:mm:ss\"", Locale.getDefault())

            positions.forEach { position ->
                val rxDateTime = dateFormat.format((position.time.toLong() * 1000L).toInstant().toDate())
                val latitude = (position.latitude_i ?: 0) * 1e-7
                val longitude = (position.longitude_i ?: 0) * 1e-7
                val altitude = position.altitude
                val satsInView = position.sats_in_view
                val speed = position.ground_speed
                val heading = "%.2f".format((position.ground_track ?: 0) * 1e-5)

                writer.appendLine(
                    "$rxDateTime,\"$latitude\",\"$longitude\",\"$altitude\",\"$satsInView\",\"$speed\",\"$heading\"",
                )
            }
        }
    }

    private suspend inline fun writeToUri(uri: Uri, crossinline block: suspend (BufferedWriter) -> Unit) =
        withContext(dispatchers.io) {
            try {
                app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                    FileWriter(parcelFileDescriptor.fileDescriptor).use { fileWriter ->
                        BufferedWriter(fileWriter).use { writer -> block.invoke(writer) }
                    }
                }
            } catch (ex: FileNotFoundException) {
                Logger.e(ex) { "Can't write file error" }
            }
        }

    override fun decodeBase64(base64: String): ByteArray =
        android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
}
