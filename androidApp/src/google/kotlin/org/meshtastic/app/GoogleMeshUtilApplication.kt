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
package org.meshtastic.app

import androidx.appfunctions.AppFunctionConfiguration
import org.koin.java.KoinJavaComponent.getKoin
import org.meshtastic.app.ai.appfunctions.AppFunctionStateSync
import org.meshtastic.app.ai.appfunctions.MeshtasticAppFunctions

/**
 * Google flavor Application subclass that configures App Functions.
 *
 * Registers a custom factory so the AppFunctions runtime can instantiate [MeshtasticAppFunctions] with its Koin-managed
 * dependencies.
 */
class GoogleMeshUtilApplication :
    MeshUtilApplication(),
    AppFunctionConfiguration.Provider {

    override fun onCreate() {
        super.onCreate()
        // Start the AppFunctions enabled-state sync. Resolved here (after startKoin has bound
        // androidContext) rather than via createdAtStart so that Koin graphs built outside a
        // running app — verification tests, previews — stay lazily constructible.
        getKoin().get<AppFunctionStateSync>()
    }

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() =
            AppFunctionConfiguration.Builder()
                .addEnclosingClassFactory(MeshtasticAppFunctions::class.java) {
                    getKoin().get<MeshtasticAppFunctions>()
                }
                .build()
}
