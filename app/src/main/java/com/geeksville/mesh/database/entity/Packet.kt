package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.Portnums
import com.google.protobuf.TextFormat
import java.io.IOException


@Entity(tableName = "packet")
data class Packet(@PrimaryKey val uuid: String,
                  @ColumnInfo(name = "type") val message_type: String,
                  @ColumnInfo(name = "received_date") val received_date: Long,
                  @ColumnInfo(name = "message") val raw_message: String
) {

    val proto: MeshProtos.MeshPacket?
        get() {
            if (message_type == "packet") {
                val builder = MeshProtos.MeshPacket.newBuilder()
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
            return proto?.run {
                if (hasDecoded() && decoded.portnumValue == Portnums.PortNum.POSITION_APP_VALUE) {
                    return MeshProtos.Position.parseFrom(decoded.payload)
                }
                return null
            }
        }
}