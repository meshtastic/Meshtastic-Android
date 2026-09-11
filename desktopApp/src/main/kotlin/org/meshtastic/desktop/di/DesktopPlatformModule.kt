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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.desktop.DesktopBuildConfig

/**
 * Synthetic [LifecycleOwner] that stays permanently in [Lifecycle.State.RESUMED]. Replaces Android's
 * `ProcessLifecycleOwner` for desktop.
 */
internal class DesktopProcessLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle
        get() = registry
}

/**
 * Desktop platform infrastructure module: [BuildConfigProvider] and the process [Lifecycle].
 *
 * The DataStore instances the `commonMain` implementations need live in [DesktopPreferencesDataStoreModule] and
 * [DesktopProtoDataStoreModule].
 */
@Module
class DesktopPlatformModule {

    /** Values generated at build time by `generateDesktopBuildConfig`. */
    @Single
    fun buildConfigProvider(): BuildConfigProvider = object : BuildConfigProvider {
        override val isDebug: Boolean = DesktopBuildConfig.IS_DEBUG
        override val applicationId: String = DesktopBuildConfig.APPLICATION_ID
        override val versionCode: Int = DesktopBuildConfig.VERSION_CODE
        override val versionName: String = DesktopBuildConfig.VERSION_NAME
        override val absoluteMinFwVersion: String = DesktopBuildConfig.ABS_MIN_FW_VERSION
        override val minFwVersion: String = DesktopBuildConfig.MIN_FW_VERSION
    }

    // LifecycleRegistry holds its owner weakly, so the owner is a definition too:
    // once it is collected, addObserver silently stops registering.
    @Single internal fun processLifecycleOwner(): DesktopProcessLifecycleOwner = DesktopProcessLifecycleOwner()

    @Single
    @Named(PROCESS_LIFECYCLE)
    internal fun processLifecycle(owner: DesktopProcessLifecycleOwner): Lifecycle = owner.lifecycle
}
