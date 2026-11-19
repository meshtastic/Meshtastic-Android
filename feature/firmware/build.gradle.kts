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
    alias(libs.plugins.meshtastic.kmp.feature)
    alias(libs.plugins.meshtastic.kmp.web)
    // Shares the bounded zip extraction (ZipExtraction.kt) between the Android and desktop JVM file handlers, which
    // previously carried two independent copies of the same logic.
    alias(libs.plugins.meshtastic.kmp.jvm.android)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ble)
            implementation(projects.core.common)
            implementation(projects.core.datastore)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.core.repository)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.resources)
            implementation(projects.core.ui)

            implementation(libs.coil)
            implementation(libs.ktor.client.core)
            implementation(libs.markdown.renderer)
            implementation(libs.markdown.renderer.m3)
        }

        // WebZipArchive inflates through DecompressionStream, which org.khronos.webgl's typed arrays carry.
        meshtasticWeb.wasmJsMain { dependencies { implementation(libs.kotlinx.browser) } }

        // Wi-Fi OTA opens a TCP socket and listens for a UDP broadcast, which a browser cannot.
        getByName("nonWebMain").dependencies { implementation(libs.ktor.network) }

        androidMain.dependencies {
            implementation(libs.markdown.renderer.android)
            // AndroidFirmwareUsbManager needs UsbSerialDriver to poke DTR on the erase image's CDC port.
            // :core:network exposes usb-serial as `implementation`, so it is not on our classpath transitively —
            // feature/connections declares it the same way for the same reason.
            implementation(libs.usb.serial.android)
        }

        // performUsbUpdate resolves compose-resources strings, whose desktop implementation needs
        // the skiko-awt runtime to read the system theme.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.mock)
            implementation(libs.compose.multiplatform.ui.test)
        }
    }
}

// BLE update paths, which the browser gates off (bluetoothFirmwareUpdateSupported): a BLE radio gets no update method.
meshtasticWeb.browserTestExclusions.addAll(
    "*FirmwareUpdateViewModelTest.startUpdate reports a firmware_update_start action with the release version",
    "*FirmwareUpdateViewModelTest.an error that wrote nothing retires the recovery record",
    "*FirmwareUpdateViewModelTest.cancelling during the download retires the recovery record",
    "*FirmwareUpdateViewModelTest.a cancelled run that unwinds late leaves the replacement run alone",
    "*FirmwareUpdateViewModelTest.a Secure DFU refusal retires the recovery record",
    "*FirmwareUpdateViewModelTest.an unreadable package retires the recovery record",
    "*FirmwareUpdateViewModelTest.update method is BLE for BLE-prefixed address",
    "*FirmwareUpdateViewModelTest.the BLE bootloader upgrade action hands the offered package to the manager",
)
