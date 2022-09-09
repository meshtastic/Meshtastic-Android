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