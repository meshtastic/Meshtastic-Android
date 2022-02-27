package com.geeksville.mesh.repository.bluetooth

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface BluetoothRepositoryModule {
    companion object {
        @Provides
        fun provideBluetoothManager(application: Application): BluetoothManager {
            return application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }

        @Provides
        fun provideBluetoothAdapter(service: BluetoothManager): BluetoothAdapter {
            return service.adapter
        }
    }
}