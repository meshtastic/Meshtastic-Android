package com.geeksville.mesh.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.entity.Packet

@Database(entities = [Packet::class], version = 1, exportSchema = false)
abstract class MeshtasticDatabase : RoomDatabase() {
    abstract fun packetDao(): PacketDao
}