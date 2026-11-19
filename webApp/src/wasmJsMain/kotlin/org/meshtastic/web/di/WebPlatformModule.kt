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

import androidx.lifecycle.Lifecycle
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.web.WebBuildConfig
import org.meshtastic.web.db.WebDatabaseManager
import org.meshtastic.web.lifecycle.WebProcessLifecycleOwner

/** Web platform infrastructure: [BuildConfigProvider], the process [Lifecycle] and the single-database manager. */
@Module
class WebPlatformModule {

    /** Values generated at build time by `generateWebBuildConfig`. */
    @Single
    fun buildConfigProvider(): BuildConfigProvider = object : BuildConfigProvider {
        override val isDebug: Boolean = WebBuildConfig.IS_DEBUG
        override val applicationId: String = WebBuildConfig.APPLICATION_ID
        override val versionCode: Int = WebBuildConfig.VERSION_CODE
        override val versionName: String = WebBuildConfig.VERSION_NAME
        override val absoluteMinFwVersion: String = WebBuildConfig.ABS_MIN_FW_VERSION
        override val minFwVersion: String = WebBuildConfig.MIN_FW_VERSION
    }

    @Single internal fun processLifecycleOwner(): WebProcessLifecycleOwner = WebProcessLifecycleOwner()

    @Single
    @Named(PROCESS_LIFECYCLE)
    internal fun processLifecycle(owner: WebProcessLifecycleOwner): Lifecycle = owner.lifecycle

    /** One OPFS database serves every device, so there is nothing to switch, evict or associate. */
    @Single fun databaseManager(): DatabaseManager = WebDatabaseManager()
}
