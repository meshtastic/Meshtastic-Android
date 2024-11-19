package com.geeksville.mesh.repository.network

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class NetworkRepositoryModule {
    companion object {
        @Provides
        fun provideConnectivityManager(application: Application): ConnectivityManager {
            return application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }

        @Provides
        fun provideNsdManager(application: Application): NsdManager {
            return application.getSystemService(Context.NSD_SERVICE) as NsdManager
        }
    }
}
