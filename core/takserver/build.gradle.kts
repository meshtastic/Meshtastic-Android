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
    android {
        namespace = "org.meshtastic.core.takserver"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    jvm {}

    sourceSets {
        commonMain.dependencies {
            api(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.proto)

            implementation(libs.okio)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.xmlutil.core)
            implementation(libs.xmlutil.serialization)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.network)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
        }

        jvmAndroidMain.dependencies {
            // TAKPacket-SDK for v2 compression/decompression (via JitPack).
            //
            // We depend on the `-jvm` variant directly rather than the parent
            // `com.github.meshtastic:TAKPacket-SDK` coordinate. JitPack does
            // not publish a root-level Gradle module metadata (.module) file
            // for the KMP parent, only per-target ones. With just the parent
            // POM, Gradle reads the four KMP variants (jvm, iosarm64,
            // iossimulatorarm64, metadata) as unconditional Maven deps and
            // tries to resolve them ALL against this Android consumer — the
            // iOS klibs declare `platform.type=native` with no androidJvm
            // variant, so variant selection fails with "No matching variant".
            //
            // Depending directly on `takpacket-sdk-jvm` skips the parent POM
            // entirely and goes straight to the JVM artifact's own module
            // metadata, which is compatible with both `jvm()` and Android
            // targets in this `jvmAndroidMain` source set. It still pulls
            // zstd-jni + xpp3 + wire-runtime-jvm + kotlin-stdlib as
            // transitive deps from the JVM variant's POM.
            //
            // zstd-jni's @aar variant is still declared explicitly in the
            // androidMain source set below so Android gets the .so files.
            implementation("com.github.meshtastic.TAKPacket-SDK:takpacket-sdk-jvm:v0.1.3") {
                // The SDK's jvmMain declares zstd-jni as a runtime dep (standard
                // JAR with desktop native libs). Android needs the @aar variant
                // instead (ships arm/arm64/x86/x86_64 .so files). Both packaging
                // formats contain the same Java classes, so Android's dex merger
                // hits "Duplicate class" errors if both land on the classpath.
                // Exclude here; androidMain re-adds it as @aar below, and jvmMain
                // re-adds the JAR for desktop.
                exclude(group = "com.github.luben", module = "zstd-jni")
            }
        }

        jvmMain.dependencies {
            // Desktop JVM: standard JAR bundles native libs for desktop archs.
            implementation("com.github.luben:zstd-jni:1.5.7-7")
        }

        androidMain.dependencies {
            // Android: @aar variant ships .so files for arm/arm64/x86/x86_64.
            // Without this, zstd-jni's ZstdDictCompress.<clinit> throws
            // UnsatisfiedLinkError and poisons TakV2Compressor permanently.
            implementation("com.github.luben:zstd-jni:1.5.7-7@aar")
        }

        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.kotest.assertions)
            implementation(libs.kotest.property)
        }
    }
}
