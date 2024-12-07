/*
 * Copyright (c) 2024 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.dao.MeshLogDao
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.database.dao.QuickChatActionDao
import com.geeksville.mesh.database.entity.ContactSettings
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.database.entity.ReactionEntity

@Database(
    entities = [
        MyNodeEntity::class,
        NodeEntity::class,
        Packet::class,
        ContactSettings::class,
        MeshLog::class,
        QuickChatAction::class,
        ReactionEntity::class,
    ],
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13, spec = AutoMigration12to13::class),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
    ],
    version = 15,
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

@DeleteTable.Entries(
    DeleteTable(tableName = "NodeInfo"),
    DeleteTable(tableName = "MyNodeInfo")
)
class AutoMigration12to13 : AutoMigrationSpec
