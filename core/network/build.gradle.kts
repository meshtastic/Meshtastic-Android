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

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain: a browser cannot open raw TCP sockets (TcpTransport, mqtt-client-transport-tcp). jvmAndroid nests
    // inside nonWeb because a project takes one hierarchy template, so meshtastic.kmp.jvm.android is not applied.
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

    // The predicate reaches only the iOS leaf compilations, not iosMain itself.
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
            // mqtt-client 0.4.0 splits into BOM + core + transport modules. `api` (not `implementation`)
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
            // TLSConfigBuilder (MqttTlsTrust.kt) needs this on every target; raw sockets stay nonWeb only.
            implementation(libs.ktor.network)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kermit)
            implementation(libs.jetbrains.lifecycle.runtime)
        }

        getByName("nonWebMain").dependencies { api(libs.meshtastic.mqtt.client.transport.tcp) }

        getByName("jvmMain") {
            dependencies {
                implementation(libs.ktor.client.java)
                implementation(libs.jserialcomm)
                implementation(libs.jmdns)
            }
        }

        androidMain.dependencies { implementation(libs.usb.serial.android) }

        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }

        commonTest.dependencies { implementation(libs.ktor.client.mock) }

        // Tests that use Kable or core:testing, neither of which has a wasmJs target.
        getByName("nonWebTest").dependencies {
            implementation(projects.core.testing)
            implementation(libs.kable.core) // Kable exception types for BLE failure-injection tests
        }
    }
}
