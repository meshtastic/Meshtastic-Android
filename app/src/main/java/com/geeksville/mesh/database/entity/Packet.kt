package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.DataPacket

@Entity(tableName = "packet")
data class Packet(
    @PrimaryKey(autoGenerate = true) val uuid: Long,
    @ColumnInfo(name = "port_num") val port_num: Int,
    @ColumnInfo(name = "contact_key") val contact_key: String,
    @ColumnInfo(name = "received_time") val received_time: Long,
    @ColumnInfo(name = "data") val data: DataPacket
) {
}
