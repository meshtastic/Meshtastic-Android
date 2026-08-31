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

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    android { withHostTest {} }

    // Library module: bare wasmJs(), no browser() — see core:prefs/build.gradle.kts's comment for why the
    // repo-wide browser() experiment was reverted.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    // nonWebMain: androidx.datastore:datastore (DataStore<T>/OkioSerializer/CorruptionException) and
    // androidx.datastore.preferences (the Preferences type) both publish no wasmJs variant at all — confirmed
    // against their real Gradle Module Metadata, the same absence core:prefs hit for datastore-preferences alone
    // (see core/prefs/build.gradle.kts). So the four proto serializers, CorePreferencesDataStore, and its three
    // Preferences-backed consumers (RecentAddressesDataSource/BootloaderWarningDataSource/
    // FirmwareRecoveryDataSource) all live here instead of commonMain. The four proto DataSources themselves
    // (ChannelSetDataSource etc.) stay in commonMain unchanged — they depend only on the platform-neutral Store<T>
    // abstraction (see core/datastore/store/Store.kt), never on DataStore<T> directly. Predicate, not
    // withAndroidTarget()/withApple() — those silently drop androidMain under
    // com.android.kotlin.multiplatform.library (KT-80409). See core/ble/build.gradle.kts for the same pattern.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    // The predicate above misses iosMain itself (only reaches the two leaf iOS compilations), so any
    // nonWebMain-only actual/declaration can't see nonWebMain's members without this explicit edge.
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(libs.meshtastic.protobufs)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
            // api, not implementation: androidApp (e.g. the google flavor's GoogleMapsDataStore/GoogleMapsPrefs) and
            // other consumers import okio.* directly today with no dependency of their own, relying entirely on this
            // module exposing it transitively (previously via androidx.datastore's own api-exposed okio dependency,
            // now directly since androidx.datastore itself moved to nonWebMain — see below).
            api(libs.okio)
        }

        // android/jvm/ios only (see hierarchy template above) — androidx.datastore has no wasmJs variant, and the
        // serializers/CorePreferencesDataStore (the sole consumers) live here. api, not implementation: androidApp's
        // google flavor (GoogleMapsDataStore.kt/GoogleMapsPrefs.kt) imports androidx.datastore.* directly with no
        // dependency of its own, relying entirely on this module's transitive exposure — confirmed by grepping
        // androidApp/build.gradle.kts for a direct dependency (none exists). An intermediate source set's `api`
        // dependency still propagates to every consumer of the targets under it (android/jvm/iOS here), while
        // correctly staying invisible to wasmJs consumers, exactly preserving the original commonMain-level `api`'s
        // effective reach.
        getByName("nonWebMain").dependencies {
            api(libs.androidx.datastore)
            api(libs.androidx.datastore.preferences)
        }

        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }

        // The only commonTest file (RecentAddressesDataSourceTest) constructs a real DataStore<Preferences> via
        // PreferenceDataStoreFactory, so it moved to nonWebTest wholesale (same split core:prefs/core:database's
        // DataStore-backed tests got) — commonTest is empty now.
    }
}
