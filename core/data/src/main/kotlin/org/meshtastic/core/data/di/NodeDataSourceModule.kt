/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.data.datasource.NodeInfoReadDataSource
import org.meshtastic.core.data.datasource.NodeInfoWriteDataSource
import org.meshtastic.core.data.datasource.SwitchingNodeInfoReadDataSource
import org.meshtastic.core.data.datasource.SwitchingNodeInfoWriteDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NodeDataSourceModule {
    @Binds @Singleton
    fun bindNodeInfoReadDataSource(impl: SwitchingNodeInfoReadDataSource): NodeInfoReadDataSource

    @Binds @Singleton
    fun bindNodeInfoWriteDataSource(impl: SwitchingNodeInfoWriteDataSource): NodeInfoWriteDataSource
}
