package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.geeksville.mesh.DataPacket

@Entity(
    tableName = "packet",
    indices = [
        Index(value = ["myNodeNum"]),
        Index(value = ["port_num"]),
        Index(value = ["contact_key"]),
    ]
)

data class Packet(
    @PrimaryKey(autoGenerate = true) val uuid: Long,
    @ColumnInfo(name = "myNodeNum", defaultValue = "0") val myNodeNum: Int,
    @ColumnInfo(name = "port_num") val port_num: Int,
    @ColumnInfo(name = "contact_key") val contact_key: String,
    @ColumnInfo(name = "received_time") val received_time: Long,
    @ColumnInfo(name = "read", defaultValue = "1") val read: Boolean,
    @ColumnInfo(name = "data") val data: DataPacket,
    @ColumnInfo(name = "packet_id", defaultValue = "0") val packetId: Int = 0,
    @ColumnInfo(name = "routing_error", defaultValue = "-1") var routingError: Int = -1,
)

@Entity(tableName = "contact_settings")
data class ContactSettings(
    @PrimaryKey val contact_key: String,
    val muteUntil: Long = 0L,
) {
    val isMuted get() = System.currentTimeMillis() <= muteUntil
}
