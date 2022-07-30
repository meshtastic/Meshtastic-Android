package com.geeksville.mesh.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.entity.Packet

@Database(entities = [Packet::class], version = 1, exportSchema = false)
abstract class MeshtasticDatabase : RoomDatabase() {
    abstract fun packetDao(): PacketDao

    companion object {
        fun getDatabase(context: Context): MeshtasticDatabase {

            return Room.databaseBuilder(
                context.applicationContext,
                MeshtasticDatabase::class.java,
                "meshtastic_database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}