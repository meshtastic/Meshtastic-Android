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
package org.meshtastic.web.di

import org.koin.core.annotation.Module
import org.meshtastic.core.ble.di.CoreBleModule
import org.meshtastic.core.common.di.CoreCommonModule
import org.meshtastic.core.data.di.CoreDataModule
import org.meshtastic.core.database.di.CoreDatabaseModule
import org.meshtastic.core.datastore.di.CoreDatastoreModule
import org.meshtastic.core.datastore.di.CoreDatastoreWasmJsModule
import org.meshtastic.core.network.di.CoreNetworkModule
import org.meshtastic.core.prefs.di.CorePrefsModule
import org.meshtastic.core.prefs.di.CorePrefsWasmJsModule
import org.meshtastic.core.service.di.CoreServiceModule
import org.meshtastic.core.ui.di.CoreUiModule
import org.meshtastic.feature.connections.di.FeatureConnectionsModule
import org.meshtastic.feature.discovery.di.FeatureDiscoveryModule
import org.meshtastic.feature.docs.di.FeatureDocsModule
import org.meshtastic.feature.firmware.di.FeatureFirmwareModule
import org.meshtastic.feature.intro.di.FeatureIntroModule
import org.meshtastic.feature.messaging.di.FeatureMessagingModule
import org.meshtastic.feature.node.di.FeatureNodeModule
import org.meshtastic.feature.settings.di.FeatureSettingsModule
import org.meshtastic.feature.wifiprovision.di.FeatureWifiProvisionModule

/**
 * Aggregate Koin module for the Web target, mirroring `DesktopKoinModule`. TAK, the map and the feature modules with no
 * wasmJs target are left out; the web modules come last so their bindings win.
 */
@Module(
    includes =
    [
        org.meshtastic.core.di.di.CoreDiModule::class,
        CoreCommonModule::class,
        CoreBleModule::class,
        CoreDataModule::class,
        org.meshtastic.core.domain.di.CoreDomainModule::class,
        CoreDatabaseModule::class,
        org.meshtastic.core.repository.di.CoreRepositoryModule::class,
        CoreDatastoreModule::class,
        CoreDatastoreWasmJsModule::class,
        CorePrefsModule::class,
        CorePrefsWasmJsModule::class,
        CoreServiceModule::class,
        CoreNetworkModule::class,
        CoreUiModule::class,
        FeatureNodeModule::class,
        FeatureMessagingModule::class,
        FeatureConnectionsModule::class,
        FeatureIntroModule::class,
        FeatureDocsModule::class,
        FeatureWifiProvisionModule::class,
        FeatureFirmwareModule::class,
        FeatureDiscoveryModule::class,
        FeatureSettingsModule::class,
        WebDiModule::class,
        WebPlatformModule::class,
        WebRuntimeModule::class,
        WebStubsModule::class,
    ],
)
class WebKoinModule
