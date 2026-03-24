/*
 * Copyright (c) 2026 Meshtastic LLC
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

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.di.CoreBleAndroidModule
import org.meshtastic.core.ble.di.CoreBleModule
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.di.CoreCommonModule
import org.meshtastic.core.data.di.CoreDataAndroidModule
import org.meshtastic.core.data.di.CoreDataModule
import org.meshtastic.core.database.di.CoreDatabaseAndroidModule
import org.meshtastic.core.database.di.CoreDatabaseModule
import org.meshtastic.core.datastore.di.CoreDatastoreAndroidModule
import org.meshtastic.core.datastore.di.CoreDatastoreModule
import org.meshtastic.core.network.di.CoreNetworkAndroidModule
import org.meshtastic.core.network.di.CoreNetworkModule
import org.meshtastic.core.network.repository.ProbeTableProvider
import org.meshtastic.core.prefs.di.CorePrefsAndroidModule
import org.meshtastic.core.prefs.di.CorePrefsModule
import org.meshtastic.core.service.di.CoreServiceAndroidModule
import org.meshtastic.core.service.di.CoreServiceModule
import org.meshtastic.core.ui.di.CoreUiModule
import org.meshtastic.feature.connections.di.FeatureConnectionsModule
import org.meshtastic.feature.firmware.di.FeatureFirmwareModule
import org.meshtastic.feature.intro.di.FeatureIntroModule
import org.meshtastic.feature.map.di.FeatureMapModule
import org.meshtastic.feature.messaging.di.FeatureMessagingModule
import org.meshtastic.feature.node.di.FeatureNodeModule
import org.meshtastic.feature.settings.di.FeatureSettingsModule
import org.meshtastic.feature.widget.di.FeatureWidgetModule

@Module(
    includes =
    [
        org.meshtastic.app.MainKoinModule::class,
        org.meshtastic.core.di.di.CoreDiModule::class,
        CoreCommonModule::class,
        CoreBleModule::class,
        CoreBleAndroidModule::class,
        CoreDataModule::class,
        CoreDataAndroidModule::class,
        org.meshtastic.core.domain.di.CoreDomainModule::class,
        CoreDatabaseModule::class,
        CoreDatabaseAndroidModule::class,
        org.meshtastic.core.repository.di.CoreRepositoryModule::class,
        CoreDatastoreModule::class,
        CoreDatastoreAndroidModule::class,
        CorePrefsModule::class,
        CorePrefsAndroidModule::class,
        CoreServiceModule::class,
        CoreServiceAndroidModule::class,
        CoreNetworkModule::class,
        CoreNetworkAndroidModule::class,
        CoreUiModule::class,
        FeatureNodeModule::class,
        FeatureMessagingModule::class,
        FeatureConnectionsModule::class,
        FeatureMapModule::class,
        FeatureSettingsModule::class,
        FeatureFirmwareModule::class,
        FeatureIntroModule::class,
        FeatureWidgetModule::class,
        NetworkModule::class,
        FlavorModule::class,
    ],
)
class AppKoinModule {
    @Single
    @Named("ProcessLifecycle")
    fun provideProcessLifecycle(): Lifecycle = ProcessLifecycleOwner.get().lifecycle

    @Single
    fun provideBuildConfigProvider(): BuildConfigProvider = object : BuildConfigProvider {
        override val isDebug: Boolean = org.meshtastic.app.BuildConfig.DEBUG
        override val applicationId: String = org.meshtastic.app.BuildConfig.APPLICATION_ID
        override val versionCode: Int = org.meshtastic.app.BuildConfig.VERSION_CODE
        override val versionName: String = org.meshtastic.app.BuildConfig.VERSION_NAME
        override val absoluteMinFwVersion: String = org.meshtastic.app.BuildConfig.ABS_MIN_FW_VERSION
        override val minFwVersion: String = org.meshtastic.app.BuildConfig.MIN_FW_VERSION
    }

    @Single fun provideWorkManager(context: Application): WorkManager = WorkManager.getInstance(context)

    @Single
    fun provideUsbManager(application: Application): UsbManager? =
        application.getSystemService(Context.USB_SERVICE) as UsbManager?

    @Single fun provideProbeTable(provider: ProbeTableProvider): ProbeTable = provider.get()

    @Single fun provideUsbSerialProber(probeTable: ProbeTable): UsbSerialProber = UsbSerialProber(probeTable)
}
