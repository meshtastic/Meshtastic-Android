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
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    id("meshtastic.kmp.jvm.android")
    id("meshtastic.koin")
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
            // this module. Both transports are registered: transport-tcp (tcp://-/ssl://, default) +
            // transport-ws (user-entered ws://-/wss://), composed with `+` at the client config sites.
            // No platform() on the KMP commonMain handler; reach the BOM through project.dependencies.
            api(project.dependencies.platform(libs.meshtastic.mqtt.client.bom))
            api(libs.meshtastic.mqtt.client.core)
            api(libs.meshtastic.mqtt.client.transport.tcp)
            api(libs.meshtastic.mqtt.client.transport.ws)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.network) // raw TCP sockets for TcpTransport (KMP-common)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kermit)
            implementation(libs.jetbrains.lifecycle.runtime)
        }

        getByName("jvmMain") {
            dependencies {
                implementation(libs.ktor.client.java)
                implementation(libs.jserialcomm)
                implementation(libs.jmdns)
            }
        }

        androidMain.dependencies { implementation(libs.usb.serial.android) }

        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kable.core) // Kable exception types for BLE failure-injection tests
        }
    }
}
