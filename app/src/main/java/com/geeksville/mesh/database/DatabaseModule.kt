package com.geeksville.mesh.database

import android.app.Application
import androidx.room.Room
import com.geeksville.mesh.database.dao.PacketDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {
    @Provides
    fun provideDatabase(application: Application): MeshtasticDatabase {
        return Room.databaseBuilder(
            application.applicationContext,
            MeshtasticDatabase::class.java,
            "meshtastic_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun providePacketDao(database: MeshtasticDatabase): PacketDao {
        return database.packetDao()
    }
}