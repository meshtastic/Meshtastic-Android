/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
    id("meshtastic.koin")
}

kotlin {
    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.core.network"
        androidResources.enable = false
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.repository)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.proto)

            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kermit)
        }

        androidMain.dependencies {
            implementation(libs.org.eclipse.paho.client.mqttv3)
            implementation(libs.coil.network.okhttp)
            implementation(libs.coil.svg)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp3.logging.interceptor)
        }
    }
}

val marketplaceAttr = Attribute.of("marketplace", String::class.java)

configurations.all {
    if (name.contains("android", ignoreCase = true)) {
        attributes.attribute(marketplaceAttr, "fdroid")
    }
}
