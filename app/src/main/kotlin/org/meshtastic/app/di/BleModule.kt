/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.native
import no.nordicsemi.kotlin.ble.core.android.AndroidEnvironment
import no.nordicsemi.kotlin.ble.environment.android.NativeAndroidEnvironment
import org.meshtastic.core.ble.AndroidBleConnectionFactory
import org.meshtastic.core.ble.AndroidBleScanner
import org.meshtastic.core.ble.AndroidBluetoothRepository
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    @Binds @Singleton
    abstract fun bindBleScanner(impl: AndroidBleScanner): BleScanner

    @Binds @Singleton
    abstract fun bindBluetoothRepository(impl: AndroidBluetoothRepository): BluetoothRepository

    @Binds @Singleton
    abstract fun bindBleConnectionFactory(impl: AndroidBleConnectionFactory): BleConnectionFactory

    companion object {
        @Provides
        @Singleton
        fun provideAndroidEnvironment(@ApplicationContext context: Context): AndroidEnvironment =
            NativeAndroidEnvironment.getInstance(context, isNeverForLocationFlagSet = true)

        @Provides
        @Singleton
        fun provideBleSingletonCoroutineScope(dispatchers: CoroutineDispatchers): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.default)

        @Provides
        @Singleton
        fun provideCentralManager(environment: AndroidEnvironment, coroutineScope: CoroutineScope): CentralManager =
            CentralManager.native(environment as NativeAndroidEnvironment, coroutineScope)

        @Provides
        fun provideBleConnection(factory: BleConnectionFactory, coroutineScope: CoroutineScope): BleConnection =
            factory.create(coroutineScope, "BLE")
    }
}
