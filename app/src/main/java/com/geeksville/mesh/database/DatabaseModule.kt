package com.geeksville.mesh.database

import android.app.Application
import com.geeksville.mesh.database.dao.MeshLogDao
import com.geeksville.mesh.database.dao.MyNodeInfoDao
import com.geeksville.mesh.database.dao.NodeInfoDao
import com.geeksville.mesh.database.dao.PacketDao
import com.geeksville.mesh.database.dao.QuickChatActionDao
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
    fun provideMyNodeInfoDao(database: MeshtasticDatabase): MyNodeInfoDao {
        return database.myNodeInfoDao()
    }

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
}