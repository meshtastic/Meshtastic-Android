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
}

kotlin {
    android {
        withHostTest { isIncludeAndroidResources = true }
        withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.meshtastic.protobufs)
            api(projects.core.common)
            api(projects.core.resources)

            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            implementation(libs.kermit)
            api(libs.okio)
            api(libs.compose.multiplatform.resources)
        }
        jvmAndroidMain.dependencies {
            // TAKPacket-SDK publishes only JVM artifacts since 0.5.2 (proto types
            // now come from the protobufs SDK above; the CoT conversion pipeline is
            // zstd-jni/xpp3-bound). Scoped to jvmAndroidMain so iOS compilations
            // never try to resolve it — iOS code goes through the expect/actual
            // seams in :core:takserver instead.
            api(libs.takpacket.sdk.kmp.get().toString()) {
                exclude(group = "com.github.luben", module = "zstd-jni")
                exclude(group = "org.ogce", module = "xpp3")
            }
        }
        androidMain.dependencies {
            api(libs.androidx.annotation)
            api(libs.androidx.core.ktx)
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.runner)
            }
        }

        commonTest.dependencies { implementation(projects.core.testing) }
    }
}
