package com.geeksville.mesh.database

import android.app.Application
import com.geeksville.mesh.database.dao.PacketDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(app: Application): MeshtasticDatabase =
        MeshtasticDatabase.getDatabase(app)

    @Provides
    fun providePacketDao(database: MeshtasticDatabase): PacketDao {
        return database.packetDao()
    }
}