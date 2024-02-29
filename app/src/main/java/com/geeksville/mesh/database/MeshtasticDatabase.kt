package com.geeksville.mesh.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.dao.MeshLogDao
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.database.dao.QuickChatActionDao
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.QuickChatAction

@Database(
    entities = [
        MyNodeInfo::class,
        NodeInfo::class,
        Packet::class,
        MeshLog::class,
        QuickChatAction::class
    ],
    autoMigrations = [
        AutoMigration (from = 3, to = 4),
        AutoMigration (from = 4, to = 5),
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MeshtasticDatabase : RoomDatabase() {
    abstract fun nodeInfoDao(): NodeInfoDao
    abstract fun packetDao(): PacketDao
    abstract fun meshLogDao(): MeshLogDao
    abstract fun quickChatActionDao(): QuickChatActionDao

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
