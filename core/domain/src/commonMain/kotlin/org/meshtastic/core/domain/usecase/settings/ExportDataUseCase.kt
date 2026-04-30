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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.BufferedSink
import org.koin.core.annotation.Single
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.positionToMeter
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.PortNum
import kotlin.math.roundToInt
import kotlin.time.Instant
import org.meshtastic.proto.Position as ProtoPosition

/** Use case for exporting persisted packet data to a CSV format. */
@Single
open class ExportDataUseCase
constructor(
    private val nodeRepository: NodeRepository,
    private val meshLogRepository: MeshLogRepository,
) {
    /**
     * Writes all persisted packet data to the provided [BufferedSink].
     *
     * @param sink The sink to output the CSV data to.
     * @param myNodeNum The node number of the current device.
     * @param filterPortnum If provided, only packets with this port number will be exported.
     */
    @Suppress("detekt:CyclomaticComplexMethod", "detekt:LongMethod", "detekt:NestedBlockDepth")
    suspend operator fun invoke(sink: BufferedSink, myNodeNum: Int, filterPortnum: Int? = null) {
        val nodes = nodeRepository.nodeDBbyNum.value
        val positionToPos: (ProtoPosition?) -> Position? = { meshPosition ->
            meshPosition?.let { Position(it) }?.takeIf { it.isValid() }
        }

        val nodePositions = mutableMapOf<Int, ProtoPosition?>()

        @Suppress("MaxLineLength")
        sink.writeUtf8(
            "\"date\",\"time\",\"from\",\"sender name\",\"sender lat\",\"sender long\",\"rx lat\",\"rx long\",\"rx elevation\",\"rx snr\",\"distance(m)\",\"hop limit\",\"payload\"\n",
        )

        meshLogRepository.getAllLogsInReceiveOrder(Int.MAX_VALUE).first().forEach { packet ->
            packet.nodeInfo?.let { nodeInfo ->
                positionToPos.invoke(nodeInfo.position)?.let { nodePositions[nodeInfo.num] = nodeInfo.position }
            }

            packet.meshPacket?.let { proto ->
                packet.position?.let { position ->
                    positionToPos.invoke(position)?.let {
                        nodePositions[proto.from.takeIf { it != 0 } ?: myNodeNum] = position
                    }
                }

                if (
                    (filterPortnum == null || (proto.decoded?.portnum?.value ?: 0) == filterPortnum) &&
                    proto.rx_snr != 0.0f
                ) {
                    val timeZone = TimeZone.currentSystemDefault()
                    val rxDateTimeObj = Instant.fromEpochMilliseconds(packet.received_date).toLocalDateTime(timeZone)
                    val timeString = rxDateTimeObj.time.toString().substringBefore('.')
                    val rxDateTime = "\"${rxDateTimeObj.date}\",\"$timeString\""
                    val rxFrom = proto.from.toUInt()
                    val senderName = nodes[proto.from]?.user?.long_name ?: ""

                    val senderPosition = nodePositions[proto.from]
                    val senderPos = positionToPos.invoke(senderPosition)
                    val senderLat = senderPos?.latitude ?: ""
                    val senderLong = senderPos?.longitude ?: ""

                    val rxPosition = nodePositions[myNodeNum]
                    val rxPos = positionToPos.invoke(rxPosition)
                    val rxLat = rxPos?.latitude ?: ""
                    val rxLong = rxPos?.longitude ?: ""
                    val rxAlt = rxPos?.altitude ?: ""
                    val rxSnr = proto.rx_snr

                    val dist =
                        if (senderPos == null || rxPos == null) {
                            ""
                        } else {
                            positionToMeter(Position(rxPosition!!), Position(senderPosition!!)).roundToInt().toString()
                        }

                    val hopLimit = proto.hop_limit
                    val decoded = proto.decoded
                    val encrypted = proto.encrypted
                    val payload =
                        when {
                            (decoded?.portnum?.value ?: 0) !in
                                setOf(PortNum.TEXT_MESSAGE_APP.value, PortNum.RANGE_TEST_APP.value) ->
                                "<${decoded?.portnum}>"

                            decoded != null -> decoded.payload.utf8().replace("\"", "\"\"")

                            encrypted != null -> "${encrypted.size} encrypted bytes"

                            else -> ""
                        }

                    @Suppress("MaxLineLength")
                    sink.writeUtf8(
                        "$rxDateTime,\"$rxFrom\",\"$senderName\",\"$senderLat\",\"$senderLong\",\"$rxLat\",\"$rxLong\",\"$rxAlt\",\"$rxSnr\",\"$dist\",\"$hopLimit\",\"$payload\"\n",
                    )
                }
            }
        }
        sink.flush()
    }
}
