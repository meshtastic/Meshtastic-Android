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
package org.meshtastic.desktop.di

import org.koin.core.annotation.Module
import org.meshtastic.core.ble.di.CoreBleModule
import org.meshtastic.core.common.di.CoreCommonModule
import org.meshtastic.core.data.di.CoreDataModule
import org.meshtastic.core.database.di.CoreDatabaseModule
import org.meshtastic.core.datastore.di.CoreDatastoreModule
import org.meshtastic.core.network.di.CoreNetworkModule
import org.meshtastic.core.prefs.di.CorePrefsModule
import org.meshtastic.core.service.di.CoreServiceModule
import org.meshtastic.core.takserver.di.CoreTakServerModule
import org.meshtastic.core.ui.di.CoreUiModule
import org.meshtastic.feature.connections.di.FeatureConnectionsModule
import org.meshtastic.feature.discovery.di.FeatureDiscoveryModule
import org.meshtastic.feature.docs.di.FeatureDocsModule
import org.meshtastic.feature.firmware.di.FeatureFirmwareModule
import org.meshtastic.feature.intro.di.FeatureIntroModule
import org.meshtastic.feature.map.di.FeatureMapModule
import org.meshtastic.feature.messaging.di.FeatureMessagingModule
import org.meshtastic.feature.node.di.FeatureNodeModule
import org.meshtastic.feature.settings.di.FeatureSettingsModule
import org.meshtastic.feature.wifiprovision.di.FeatureWifiProvisionModule

/**
 * Aggregate Koin module for the Desktop target — the single entry [DesktopKoinApp] points at.
 *
 * Includes the `commonMain` module classes from the core KMP libraries (prefs, data repositories, managers, datastore
 * data sources, use cases, and ViewModels), then the desktop-specific modules. The desktop modules come last so their
 * bindings win over any `commonMain` default they deliberately replace.
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
        CorePrefsModule::class,
        CoreServiceModule::class,
        CoreNetworkModule::class,
        CoreTakServerModule::class,
        CoreUiModule::class,
        FeatureNodeModule::class,
        FeatureMessagingModule::class,
        FeatureConnectionsModule::class,
        FeatureMapModule::class,
        FeatureSettingsModule::class,
        FeatureDiscoveryModule::class,
        FeatureDocsModule::class,
        FeatureFirmwareModule::class,
        FeatureIntroModule::class,
        FeatureWifiProvisionModule::class,
        DesktopDiModule::class,
        DesktopPlatformModule::class,
        DesktopPreferencesDataStoreModule::class,
        DesktopProtoDataStoreModule::class,
        DesktopRuntimeModule::class,
        DesktopStubsModule::class,
        DesktopAiModule::class,
    ],
)
class DesktopKoinModule
