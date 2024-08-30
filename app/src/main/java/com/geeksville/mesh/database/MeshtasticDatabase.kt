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
import com.geeksville.mesh.database.entity.ContactSettings
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.QuickChatAction

@Database(
    entities = [
        MyNodeInfo::class,
        NodeInfo::class,
        Packet::class,
        ContactSettings::class,
        MeshLog::class,
        QuickChatAction::class
    ],
    autoMigrations = [
        AutoMigration (from = 3, to = 4),
        AutoMigration (from = 4, to = 5),
        AutoMigration (from = 5, to = 6),
        AutoMigration (from = 6, to = 7),
        AutoMigration (from = 7, to = 8),
        AutoMigration (from = 8, to = 9),
        AutoMigration (from = 9, to = 10),
    ],
    version = 10,
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
