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

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.kmp.web)
    alias(libs.plugins.meshtastic.kmp.jvm.android)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

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

        // A browser cannot open raw TCP sockets (TcpTransport, mqtt-client-transport-tcp).
        getByName("nonWebMain").dependencies {
            api(libs.meshtastic.mqtt.client.transport.tcp)
            // The WebSocket phone link pins CIO: OkHttp would apply the app's cleartext policy to a LAN ws:// radio.
            implementation(libs.ktor.client.cio)
            // The in-process mesh node (NodeRadioTransport): node-host is the facade every platform embeds,
            // its platform factory comes from androidMain (node-android) or the desktop app (JvmBearerFactory).
            // node-kmp publishes no wasmJs, so the node and everything that names it stays nonWeb.
            api(project.dependencies.platform(libs.meshtastic.node.bom))
            api(libs.meshtastic.node.host)
            implementation(libs.meshtastic.node.store.file) // nodeFileStores and AtomicFileStore
            api(libs.meshtastic.node.sensors) // I2cBusState, a host's sensor bus as the node card shows it
        }

        getByName("jvmMain") {
            dependencies {
                implementation(libs.ktor.client.java)
                implementation(libs.jserialcomm)
                implementation(libs.jmdns)
            }
        }

        androidMain.dependencies {
            implementation(libs.usb.serial.android)
            api(libs.meshtastic.node.android) // NodeLocationFeed names its LocationFeed, which each flavor binds
        }

        meshtasticWeb.wasmJsMain { dependencies { implementation(libs.kotlinx.browser) } }

        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kable.core) // Kable exception types for BLE failure-injection tests
            implementation(libs.ktor.client.mock)
        }

        getByName("nonWebTest").dependencies {
            // FakeTransport for NodeRadioTransport tests; node-kmp publishes no wasmJs. node-testing carries
            // kotlin-test's JUnit 4 flavor transitively; this module tests on JUnit 5, and both claim one capability.
            implementation(libs.meshtastic.node.testing.get().toString()) {
                exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
            }
        }
    }
}
