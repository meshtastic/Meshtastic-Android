package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.Portnums
import com.google.protobuf.TextFormat
import java.io.IOException


@Entity(tableName = "log")
data class MeshLog(@PrimaryKey val uuid: String,
                   @ColumnInfo(name = "type") val message_type: String,
                   @ColumnInfo(name = "received_date") val received_date: Long,
                   @ColumnInfo(name = "message") val raw_message: String
) {

    val meshPacket: MeshProtos.MeshPacket?
        get() {
            if (message_type == "Packet") {
                val builder = MeshProtos.MeshPacket.newBuilder()
                try {
                    TextFormat.getParser().merge(raw_message, builder)
                    return builder.build()
                } catch (e: IOException) {
                }
            }
            return null
        }

    val nodeInfo: MeshProtos.NodeInfo?
        get() {
            if (message_type == "NodeInfo") {
                val builder = MeshProtos.NodeInfo.newBuilder()
                try {
                    TextFormat.getParser().merge(raw_message, builder)
                    return builder.build()
                } catch (e: IOException) {
                }
            }
            return null
        }

    val position: MeshProtos.Position?
        get() {
            return meshPacket?.run {
                if (hasDecoded() && decoded.portnumValue == Portnums.PortNum.POSITION_APP_VALUE) {
                    return MeshProtos.Position.parseFrom(decoded.payload)
                }
                return null
            } ?: nodeInfo?.position
        }
}
