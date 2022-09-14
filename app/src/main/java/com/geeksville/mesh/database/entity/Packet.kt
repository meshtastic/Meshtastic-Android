package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus

@Entity(tableName = "packet")
data class Packet(
    @PrimaryKey(autoGenerate = true) val uuid: Long,
    @ColumnInfo(name = "port_num") val port_num: Int,
    @ColumnInfo(name = "contact_id") val contact_id: String?,
    @ColumnInfo(name = "channel") val channel: Int,
    @ColumnInfo(name = "status") val status: MessageStatus = MessageStatus.UNKNOWN,
    @ColumnInfo(name = "received_time") val received_time: Long,
    @ColumnInfo(name = "packet") val packet: DataPacket
) {
}
