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
    id("meshtastic.kmp.jvm.android")
    id("meshtastic.koin")
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
            // JVM-only since 0.5.2 and is api()-exported by :core:model's
            // jvmAndroidMain — common code reaches it through the
            // TakSdkCompressor/TakV2Compressor/CotSanitizer expect/actual seams.
            // zstd-jni and xpp3 are excluded at the dep site in :core:model
            // and re-added per-target below.

            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.xmlutil.core)
            implementation(libs.xmlutil.serialization)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.network)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
        }

        jvmAndroidMain.dependencies {}

        jvmMain.dependencies {
            // Desktop JVM: standard JAR bundles native libs for desktop archs.
            implementation("com.github.luben:zstd-jni:1.5.7-11")
            // xpp3 is excluded from jvmAndroidMain (Android ships it as a
            // platform class), but Desktop JVM still needs it for XmlPullParser.
            implementation("org.ogce:xpp3:1.1.6")
        }

        androidMain.dependencies {
            // Android: @aar variant ships .so files for arm/arm64/x86/x86_64.
            // Without this, zstd-jni's ZstdDictCompress.<clinit> throws
            // UnsatisfiedLinkError and poisons TakV2Compressor permanently.
            implementation("com.github.luben:zstd-jni:1.5.7-11@aar")
        }

        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.property)
        }

        val androidHostTest by getting {
            dependencies {
                // Host-JVM tests need the platform JAR (not AAR) for zstd native
                // libs — the @aar from androidMain only ships ARM/x86 .so files.
                implementation("com.github.luben:zstd-jni:1.5.7-11")
            }
        }
    }
}
