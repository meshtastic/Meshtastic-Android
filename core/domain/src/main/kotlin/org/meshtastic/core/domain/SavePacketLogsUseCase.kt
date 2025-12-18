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

package org.meshtastic.core.domain

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.positionToMeter
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

class SavePacketLogsUseCase
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val nodeRepository: NodeRepository,
    private val meshLogRepository: MeshLogRepository,
) {
    @Suppress("detekt:CyclomaticComplexMethod", "detekt:LongMethod")
    suspend operator fun invoke(uri: Uri, filterPortnum: Int? = null) = withContext(Dispatchers.IO) {
        // Extract distances to this device from position messages and put (node,SNR,distance)
        // in the file_uri
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return@withContext

        // Capture the current node value
        val nodes = nodeRepository.nodeDBbyNum.value

        // Converts a MeshProtos.Position (nullable) to a Position, but only if it's valid, otherwise returns null.
        val positionToPos: (MeshProtos.Position?) -> Position? = { meshPosition ->
            meshPosition?.let { Position(it) }?.takeIf { it.isValid() }
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                FileWriter(parcelFileDescriptor.fileDescriptor).use { fileWriter ->
                    BufferedWriter(fileWriter).use { writer ->
                        val nodePositions = mutableMapOf<Int, MeshProtos.Position?>()

                        @Suppress("MaxLineLength")
                        writer.appendLine(
                            "\"date\",\"time\",\"from\",\"sender name\",\"sender lat\",\"sender long\",\"rx lat\"," +
                                "\"rx long\",\"rx elevation\",\"rx snr\",\"distance(m)\",\"hop limit\",\"payload\"",
                        )

                        // Packets are ordered by time, we keep most recent position of
                        // our device in localNodePosition.
                        val dateFormat = SimpleDateFormat("\"yyyy-MM-dd\",\"HH:mm:ss\"", Locale.getDefault())

                        // We use .first() to get the current list from the flow
                        meshLogRepository.getAllLogsInReceiveOrder(Int.MAX_VALUE).first().forEach { packet ->
                            // If we get a NodeInfo packet, use it to update our position data (if valid)
                            packet.nodeInfo?.let { nodeInfo ->
                                val pos = positionToPos.invoke(nodeInfo.position)
                                if (pos != null) {
                                    nodePositions[nodeInfo.num] = nodeInfo.position
                                }
                            }

                            packet.meshPacket?.let { proto ->
                                // If the packet contains position data then use it to update, if valid
                                packet.position?.let { position ->
                                    val pos = positionToPos.invoke(position)
                                    if (pos != null) {
                                        nodePositions[proto.from.takeIf { it != 0 } ?: myNodeNum] = position
                                    }
                                }

                                // packets must have rxSNR, and optionally match the filter given as a param.
                                if (
                                    (filterPortnum == null || proto.decoded.portnumValue == filterPortnum) &&
                                    proto.rxSnr != 0.0f
                                ) {
                                    processPacket(
                                        packet = packet,
                                        proto = proto,
                                        nodes = nodes,
                                        nodePositions = nodePositions,
                                        positionToPos = positionToPos,
                                        myNodeNum = myNodeNum,
                                        dateFormat = dateFormat,
                                        writer = writer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: FileNotFoundException) {
            Timber.e("Can't write file error: ${ex.message}")
        }
    }

    @Suppress("LongParameterList", "ComplexMethod", "LongMethod")
    private fun processPacket(
        packet: MeshLog,
        proto: MeshProtos.MeshPacket,
        nodes: Map<Int, Node>,
        nodePositions: Map<Int, MeshProtos.Position?>,
        positionToPos: (MeshProtos.Position?) -> Position?,
        myNodeNum: Int,
        dateFormat: SimpleDateFormat,
        writer: BufferedWriter,
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
                positionToMeter(Position(rxPosition!!), Position(senderPosition!!)).roundToInt().toString()
            }

        val hopLimit = proto.hopLimit

        val payload =
            when {
                proto.decoded.portnumValue !in
                    setOf(Portnums.PortNum.TEXT_MESSAGE_APP_VALUE, Portnums.PortNum.RANGE_TEST_APP_VALUE) ->
                    "<${proto.decoded.portnum}>"

                proto.hasDecoded() -> proto.decoded.payload.toStringUtf8().replace("\"", "\"\"")

                proto.hasEncrypted() -> "${proto.encrypted.size()} encrypted bytes"
                else -> ""
            }

        @Suppress("MaxLineLength")
        writer.appendLine(
            "$rxDateTime,\"$rxFrom\",\"$senderName\",\"$senderLat\",\"$senderLong\"," +
                "\"$rxLat\",\"$rxLong\",\"$rxAlt\",\"$rxSnr\",\"$dist\",\"$hopLimit\",\"$payload\"",
        )
    }
}
