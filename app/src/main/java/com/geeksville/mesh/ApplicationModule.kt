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

package com.geeksville.mesh

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.geeksville.mesh.service.MeshServiceNotifications
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.common.BuildConfigProvider
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object ApplicationModule {

    @Provides fun provideProcessLifecycleOwner(): LifecycleOwner = ProcessLifecycleOwner.get()

    @Provides
    fun provideProcessLifecycle(processLifecycleOwner: LifecycleOwner): Lifecycle = processLifecycleOwner.lifecycle

    @Provides
    fun providesMeshServiceNotifications(application: Application): MeshServiceNotifications =
        MeshServiceNotifications(application)

    @Singleton
    @Provides
    fun provideBuildConfigProvider(): BuildConfigProvider = object : BuildConfigProvider {
        override val isDebug: Boolean = BuildConfig.DEBUG
        override val applicationId: String = BuildConfig.APPLICATION_ID
        override val versionCode: Int = BuildConfig.VERSION_CODE
        override val versionName: String = BuildConfig.VERSION_NAME
        override val absoluteMinFwVersion: String = BuildConfig.ABS_MIN_FW_VERSION
        override val minFwVersion: String = BuildConfig.MIN_FW_VERSION
    }
}
