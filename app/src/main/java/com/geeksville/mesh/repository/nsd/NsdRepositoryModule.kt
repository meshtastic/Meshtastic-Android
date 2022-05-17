package com.geeksville.mesh.repository.nsd

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class NsdRepositoryModule {
    companion object {
        @Provides
        fun provideNsdManager(application: Application): NsdManager? {
            return application.getSystemService(Context.NSD_SERVICE) as NsdManager?
        }
    }
}