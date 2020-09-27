package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "packet")
data class Packet(@PrimaryKey val uuid: String,
                  @ColumnInfo(name = "type") val message_type: String,
                  @ColumnInfo(name = "received_date") val received_date: Long,
                  @ColumnInfo(name = "message") val raw_message: String
) {



}