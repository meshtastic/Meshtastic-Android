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
    alias(libs.plugins.meshtastic.kmp.jvm.android)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    @Suppress("UnstableApiUsage")
    android { withHostTest { isIncludeAndroidResources = true } }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(libs.meshtastic.protobufs)

            // org.meshtastic.proto.TAKPacketV2 and friends come from the
            // protobufs SDK (api()-exported by :core:model for every target).
            // The TAKPacket-SDK conversion pipeline (org.meshtastic.tak.*) is
            // multiplatform since 0.7.0 but consumed only on JVM/Android — it's
            // api()-exported by :core:model's jvmAndroidMain, and common code
            // reaches it through the TakSdkCompressor/TakV2Compressor/CotSanitizer
            // expect/actual seams (iOS keeps stub actuals). zstd compression and
            // CoT XML now ride on the SDK's transitive kzstd + xmlutil, so there
            // are no native zstd-jni/xpp3 deps to re-add per target.

            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.xmlutil.core)
            implementation(libs.xmlutil.serialization)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.network)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
        }

        commonTest.dependencies { implementation(projects.core.testing) }
    }
}
