/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.positionToMeter
import org.meshtastic.core.prefs.ui.UiPrefs
import org.meshtastic.core.service.IMeshService
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums
import timber.log.Timber
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@Suppress("LongParameterList")
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val app: Application,
    radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val meshLogRepository: MeshLogRepository,
    private val uiPrefs: UiPrefs,
    private val uiPreferencesDataSource: UiPreferencesDataSource,
    private val buildConfigProvider: BuildConfigProvider,
) : ViewModel() {
    val myNodeInfo: StateFlow<MyNodeEntity?> = nodeRepository.myNodeInfo

    val myNodeNum
        get() = myNodeInfo.value?.myNodeNum

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    val isConnected =
        serviceRepository.connectionState.map { it.isConnected() }.stateInWhileSubscribed(initialValue = false)

    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig.getDefaultInstance())

    val meshService: IMeshService?
        get() = serviceRepository.meshService

    val provideLocation: StateFlow<Boolean> =
        myNodeInfo
            .flatMapLatest { myNodeEntity ->
                // When myNodeInfo changes, set up emissions for the "provide-location-nodeNum" pref.
                if (myNodeEntity == null) {
                    flowOf(false)
                } else {
                    uiPrefs.shouldProvideNodeLocation(myNodeEntity.myNodeNum)
                }
            }
            .stateInWhileSubscribed(initialValue = false)

    private val _excludedModulesUnlocked = MutableStateFlow(false)
    val excludedModulesUnlocked: StateFlow<Boolean> = _excludedModulesUnlocked.asStateFlow()

    val appVersionName
        get() = buildConfigProvider.versionName

    fun setProvideLocation(value: Boolean) {
        myNodeNum?.let { uiPrefs.setShouldProvideNodeLocation(it, value) }
    }

    fun setTheme(theme: Int) {
        uiPreferencesDataSource.setTheme(theme)
    }

    fun showAppIntro() {
        uiPreferencesDataSource.setAppIntroCompleted(false)
    }

    fun unlockExcludedModules() {
        _excludedModulesUnlocked.update { true }
    }

    /**
     * Export all persisted packet data to a CSV file at the given URI.
     *
     * The CSV will include all packets, or only those matching the given port number if specified. Each row contains:
     * date, time, sender node number, sender name, sender latitude, sender longitude, receiver latitude, receiver
     * longitude, receiver elevation, received SNR, distance, hop limit, and payload.
     *
     * @param uri The destination URI for the CSV file.
     * @param filterPortnum If provided, only packets with this port number will be exported.
     */
    @Suppress("detekt:CyclomaticComplexMethod", "detekt:LongMethod")
    fun saveDataCsv(uri: Uri, filterPortnum: Int? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            // Extract distances to this device from position messages and put (node,SNR,distance)
            // in the file_uri
            val myNodeNum = myNodeNum ?: return@launch

            // Capture the current node value while we're still on main thread
            val nodes = nodeRepository.nodeDBbyNum.value

            // Converts a MeshProtos.Position (nullable) to a Position, but only if it's valid, otherwise returns null.
            // The returned Position is guaranteed to be non-null and valid, or null if the input was null or invalid.
            val positionToPos: (MeshProtos.Position?) -> Position? = { meshPosition ->
                meshPosition?.let { Position(it) }?.takeIf { it.isValid() }
            }

            writeToUri(uri) { writer ->
                val nodePositions = mutableMapOf<Int, MeshProtos.Position?>()

                @Suppress("MaxLineLength")
                writer.appendLine(
                    "\"date\",\"time\",\"from\",\"sender name\",\"sender lat\",\"sender long\",\"rx lat\",\"rx long\",\"rx elevation\",\"rx snr\",\"distance(m)\",\"hop limit\",\"payload\"",
                )

                // Packets are ordered by time, we keep most recent position of
                // our device in localNodePosition.
                val dateFormat = SimpleDateFormat("\"yyyy-MM-dd\",\"HH:mm:ss\"", Locale.getDefault())
                meshLogRepository.getAllLogsInReceiveOrder(Int.MAX_VALUE).first().forEach { packet ->
                    // If we get a NodeInfo packet, use it to update our position data (if valid)
                    packet.nodeInfo?.let { nodeInfo ->
                        positionToPos.invoke(nodeInfo.position)?.let { nodePositions[nodeInfo.num] = nodeInfo.position }
                    }

                    packet.meshPacket?.let { proto ->
                        // If the packet contains position data then use it to update, if valid
                        packet.position?.let { position ->
                            positionToPos.invoke(position)?.let {
                                nodePositions[
                                    proto.from.takeIf { it != 0 } ?: myNodeNum,
                                ] = position
                            }
                        }

                        // packets must have rxSNR, and optionally match the filter given as a param.
                        if (
                            (filterPortnum == null || proto.decoded.portnumValue == filterPortnum) &&
                            proto.rxSnr != 0.0f
                        ) {
                            val rxDateTime = dateFormat.format(packet.received_date)
                            val rxFrom = proto.from.toUInt()
                            val senderName = nodes[proto.from]?.user?.longName ?: ""

                            // sender lat & long
                            val senderPosition = nodePositions[proto.from]
                            val senderPos = positionToPos.invoke(senderPosition)
                            val senderLat = senderPos?.latitude ?: ""
                            val senderLong = senderPos?.longitude ?: ""

                            // rx lat, long, and elevation
                            val rxPosition = nodePositions[myNodeNum]
                            val rxPos = positionToPos.invoke(rxPosition)
                            val rxLat = rxPos?.latitude ?: ""
                            val rxLong = rxPos?.longitude ?: ""
                            val rxAlt = rxPos?.altitude ?: ""
                            val rxSnr = proto.rxSnr

                            // Calculate the distance if both positions are valid

                            val dist =
                                if (senderPos == null || rxPos == null) {
                                    ""
                                } else {
                                    positionToMeter(
                                        Position(rxPosition!!), // Use rxPosition but only if rxPos was
                                        // valid
                                        Position(senderPosition!!), // Use senderPosition but only if
                                        // senderPos was valid
                                    )
                                        .roundToInt()
                                        .toString()
                                }

                            val hopLimit = proto.hopLimit

                            val payload =
                                when {
                                    proto.decoded.portnumValue !in
                                        setOf(
                                            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                                            Portnums.PortNum.RANGE_TEST_APP_VALUE,
                                        ) -> "<${proto.decoded.portnum}>"

                                    proto.hasDecoded() -> proto.decoded.payload.toStringUtf8().replace("\"", "\"\"")

                                    proto.hasEncrypted() -> "${proto.encrypted.size()} encrypted bytes"
                                    else -> ""
                                }

                            //  date,time,from,sender name,sender lat,sender long,rx lat,rx long,rx
                            // elevation,rx
                            // snr,distance,hop limit,payload
                            @Suppress("MaxLineLength")
                            writer.appendLine(
                                "$rxDateTime,\"$rxFrom\",\"$senderName\",\"$senderLat\",\"$senderLong\",\"$rxLat\",\"$rxLong\",\"$rxAlt\",\"$rxSnr\",\"$dist\",\"$hopLimit\",\"$payload\"",
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend inline fun writeToUri(uri: Uri, crossinline block: suspend (BufferedWriter) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                    FileWriter(parcelFileDescriptor.fileDescriptor).use { fileWriter ->
                        BufferedWriter(fileWriter).use { writer -> block.invoke(writer) }
                    }
                }
            } catch (ex: FileNotFoundException) {
                Timber.e("Can't write file error: ${ex.message}")
            }
        }
    }
}
