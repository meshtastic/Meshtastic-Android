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
    android { withHostTest { isIncludeAndroidResources = true } }

    // Library module: bare wasmJs(), no browser() — see core:prefs/build.gradle.kts's comment for why the
    // repo-wide browser() experiment was reverted.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    // nonWebMain: raw TCP sockets (TcpTransport.kt, mqtt-client-transport-tcp) have no browser equivalent --
    // a permanent sandbox limitation, not a library gap. jvmAndroidMain nests inside it (replacing the
    // meshtastic.kmp.jvm.android convention plugin, which would need a second, conflicting
    // applyHierarchyTemplate call) so ConnectionFailures.jvmAndroid.kt keeps its existing source set.
    // Predicate, not withAndroidTarget()/withApple() -- those silently drop androidMain under
    // com.android.kotlin.multiplatform.library (KT-80409), same as core:ble.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("nonWeb") {
                withCompilations { it.target.targetName != "wasmJs" }
                group("jvmAndroid") {
                    withCompilations { it.target.targetName == "android" || it.target.targetName == "jvm" }
                }
            }
        }
    }

    // The predicate above misses iosMain itself (only reaches the two leaf iOS compilations), same gap core:ble hit.
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.ble)

            implementation(libs.okio)
            // mqtt-client 0.8.1 splits into BOM + core + transport modules. `api` (not `implementation`)
            // because :core:data and :desktopApp consume org.meshtastic.mqtt.* types transitively through
            // this module. transport-ws (user-entered ws://-/wss://) publishes a real wasmJs variant and
            // works on every target; transport-tcp (tcp://-/ssl://, the nonWeb default) does not -- see
            // nonWebMain below. mqttTransportFactory() (MqttTransportSelection.kt) composes them per platform.
            // No platform() on the KMP commonMain handler; reach the BOM through project.dependencies.
            api(project.dependencies.platform(libs.meshtastic.mqtt.client.bom))
            api(libs.meshtastic.mqtt.client.core)
            api(libs.meshtastic.mqtt.client.transport.ws)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.ktor.client.core)
            // TLSConfigBuilder (MqttTlsTrust.kt) needs this on every target; raw sockets (TcpTransport.kt)
            // stay nonWeb-only below -- ktor-network's own wasmJs variant carries the former, not the latter.
            implementation(libs.ktor.network)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kermit)
            implementation(libs.jetbrains.lifecycle.runtime)
        }

        // android/jvm/ios only (see hierarchy template above) -- browsers cannot open raw TCP sockets at all.
        getByName("nonWebMain").dependencies { api(libs.meshtastic.mqtt.client.transport.tcp) }

        getByName("jvmMain") {
            dependencies {
                implementation(libs.ktor.client.java)
                implementation(libs.jserialcomm)
                implementation(libs.jmdns)
            }
        }

        androidMain.dependencies { implementation(libs.usb.serial.android) }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }

        commonTest.dependencies { implementation(libs.ktor.client.mock) }

        // android/jvm/ios only: core:testing has no wasmJs target, and Kable (BLE failure-injection tests) has none
        // either -- same gap core:ble/core:database hit for their own commonTest.
        getByName("nonWebTest").dependencies {
            implementation(projects.core.testing)
            implementation(libs.kable.core) // Kable exception types for BLE failure-injection tests
        }
    }
}
