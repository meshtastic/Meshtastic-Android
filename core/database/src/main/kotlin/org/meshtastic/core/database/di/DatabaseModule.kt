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

package org.meshtastic.core.database.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.dao.DeviceHardwareDao
import org.meshtastic.core.database.dao.FirmwareReleaseDao
import org.meshtastic.core.database.dao.MeshLogDao
import org.meshtastic.core.database.dao.NodeInfoDao
import org.meshtastic.core.database.dao.PacketDao
import org.meshtastic.core.database.dao.QuickChatActionDao
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(app: Application): MeshtasticDatabase =
        MeshtasticDatabase.getDatabase(app)

    @Provides
    fun provideNodeInfoDao(database: MeshtasticDatabase): NodeInfoDao {
        return database.nodeInfoDao()
    }

    @Provides
    fun providePacketDao(database: MeshtasticDatabase): PacketDao {
        return database.packetDao()
    }

    @Provides
    fun provideMeshLogDao(database: MeshtasticDatabase): MeshLogDao {
        return database.meshLogDao()
    }

    @Provides
    fun provideQuickChatActionDao(database: MeshtasticDatabase): QuickChatActionDao {
        return database.quickChatActionDao()
    }

    @Provides
    fun provideDeviceHardwareDao(database: MeshtasticDatabase): DeviceHardwareDao {
        return database.deviceHardwareDao()
    }

    @Provides
    fun provideFirmwareReleaseDao(database: MeshtasticDatabase): FirmwareReleaseDao {
        return database.firmwareReleaseDao()
    }
}
