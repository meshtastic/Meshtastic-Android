/*
 * Copyright (c) 2025 Meshtastic LLC
 */

package org.meshtastic.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.data.datasource.NodeInfoDataSource
import org.meshtastic.core.data.datasource.SwitchingNodeInfoDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NodeDataSourceModule {
    @Binds
    @Singleton
    fun bindNodeInfoDataSource(impl: SwitchingNodeInfoDataSource): NodeInfoDataSource
}



