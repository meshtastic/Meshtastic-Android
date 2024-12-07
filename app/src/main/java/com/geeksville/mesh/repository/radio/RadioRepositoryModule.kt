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

package com.geeksville.mesh.repository.radio

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds

@Suppress("unused") // Used by hilt
@Module
@InstallIn(SingletonComponent::class)
abstract class RadioRepositoryModule {

    @Multibinds
    abstract fun interfaceMap(): Map<InterfaceId, @JvmSuppressWildcards InterfaceSpec<*>>

    @[Binds IntoMap InterfaceMapKey(InterfaceId.BLUETOOTH)]
    abstract fun bindBluetoothInterfaceSpec(spec: BluetoothInterfaceSpec): @JvmSuppressWildcards InterfaceSpec<*>

    @[Binds IntoMap InterfaceMapKey(InterfaceId.MOCK)]
    abstract fun bindMockInterfaceSpec(spec: MockInterfaceSpec): @JvmSuppressWildcards InterfaceSpec<*>

    @[Binds IntoMap InterfaceMapKey(InterfaceId.NOP)]
    abstract fun bindNopInterfaceSpec(spec: NopInterfaceSpec): @JvmSuppressWildcards InterfaceSpec<*>

    @[Binds IntoMap InterfaceMapKey(InterfaceId.SERIAL)]
    abstract fun bindSerialInterfaceSpec(spec: SerialInterfaceSpec): @JvmSuppressWildcards InterfaceSpec<*>

    @[Binds IntoMap InterfaceMapKey(InterfaceId.TCP)]
    abstract fun bindTCPInterfaceSpec(spec: TCPInterfaceSpec): @JvmSuppressWildcards InterfaceSpec<*>

    companion object {
        @Provides
        @RadioRepositoryQualifier
        fun provideSharedPreferences(application: Application): SharedPreferences {
            return application.getSharedPreferences("radio-prefs", Context.MODE_PRIVATE)
        }
    }
}