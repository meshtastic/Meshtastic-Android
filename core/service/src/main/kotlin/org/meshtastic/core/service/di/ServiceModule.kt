package org.meshtastic.core.service.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.service.AndroidRadioControllerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    
    @Binds
    abstract fun bindRadioController(
        impl: AndroidRadioControllerImpl
    ): RadioController
    
}
