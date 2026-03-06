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
package org.meshtastic.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.app.repository.radio.AndroidRadioInterfaceService
import org.meshtastic.app.service.AndroidAppWidgetUpdater
import org.meshtastic.app.service.AndroidMeshLocationManager
import org.meshtastic.app.service.AndroidMeshWorkerManager
import org.meshtastic.app.service.MeshServiceNotificationsImpl
import org.meshtastic.app.service.ServiceBroadcasts
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.di.ProcessLifecycle
import org.meshtastic.core.repository.MeshServiceNotifications
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
interface ApplicationModule {

    @Binds fun bindMeshServiceNotifications(impl: MeshServiceNotificationsImpl): MeshServiceNotifications

    @Binds
    fun bindMeshLocationManager(impl: AndroidMeshLocationManager): org.meshtastic.core.repository.MeshLocationManager

    @Binds fun bindMeshWorkerManager(impl: AndroidMeshWorkerManager): org.meshtastic.core.repository.MeshWorkerManager

    @Binds fun bindAppWidgetUpdater(impl: AndroidAppWidgetUpdater): org.meshtastic.core.repository.AppWidgetUpdater

    @Binds
    fun bindRadioInterfaceService(
        impl: AndroidRadioInterfaceService,
    ): org.meshtastic.core.repository.RadioInterfaceService

    @Binds fun bindServiceBroadcasts(impl: ServiceBroadcasts): org.meshtastic.core.repository.ServiceBroadcasts

    companion object {
        @Provides @ProcessLifecycle
        fun provideProcessLifecycleOwner(): LifecycleOwner = ProcessLifecycleOwner.get()

        @Provides
        @ProcessLifecycle
        fun provideProcessLifecycle(@ProcessLifecycle processLifecycleOwner: LifecycleOwner): Lifecycle =
            processLifecycleOwner.lifecycle

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
}
