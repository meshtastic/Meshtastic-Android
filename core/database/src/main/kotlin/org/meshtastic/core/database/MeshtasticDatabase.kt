/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import org.meshtastic.core.database.dao.DeviceHardwareDao
import org.meshtastic.core.database.dao.FirmwareReleaseDao
import org.meshtastic.core.database.dao.MeshLogDao
import org.meshtastic.core.database.dao.NodeInfoDao
import org.meshtastic.core.database.dao.PacketDao
import org.meshtastic.core.database.dao.QuickChatActionDao
import org.meshtastic.core.database.entity.ContactSettings
import org.meshtastic.core.database.entity.DeviceHardwareEntity
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.entity.MetadataEntity
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.database.entity.ReactionEntity

@Database(
    entities =
    [
        MyNodeEntity::class,
        NodeEntity::class,
        Packet::class,
        ContactSettings::class,
        MeshLog::class,
        QuickChatAction::class,
        ReactionEntity::class,
        MetadataEntity::class,
        DeviceHardwareEntity::class,
        FirmwareReleaseEntity::class,
    ],
    autoMigrations =
    [
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
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
    ],
    version = 20,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MeshtasticDatabase : RoomDatabase() {
    abstract fun nodeInfoDao(): NodeInfoDao

    abstract fun packetDao(): PacketDao

    abstract fun meshLogDao(): MeshLogDao

    abstract fun quickChatActionDao(): QuickChatActionDao

    abstract fun deviceHardwareDao(): DeviceHardwareDao

    abstract fun firmwareReleaseDao(): FirmwareReleaseDao

    companion object {
        fun getDatabase(context: Context): MeshtasticDatabase =
            Room.databaseBuilder(context.applicationContext, MeshtasticDatabase::class.java, "meshtastic_database")
                .fallbackToDestructiveMigration(false)
                .build()
    }
}

@DeleteTable.Entries(DeleteTable(tableName = "NodeInfo"), DeleteTable(tableName = "MyNodeInfo"))
class AutoMigration12to13 : AutoMigrationSpec
