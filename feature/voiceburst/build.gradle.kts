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

import java.io.File

plugins {
    alias(libs.plugins.meshtastic.kmp.feature)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

// ─── Codec2 JNI detection ─────────────────────────────────────────
val codec2SoArm64 = File(projectDir, "src/androidMain/jniLibs/arm64-v8a/libcodec2.so")
val codec2JniArm64 = File(projectDir, "src/androidMain/jniLibs/arm64-v8a/libcodec2_jni.so")
val codec2Available = codec2SoArm64.exists() && codec2JniArm64.exists()

if (codec2Available) {
    logger.lifecycle(":feature:voiceburst — libcodec2.so + libcodec2_jni.so trovate")
} else {
    logger.lifecycle(":feature:voiceburst — .so assenti → stub mode (esegui scripts/build_codec2.sh)")
}

kotlin {
    jvm()

    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.feature.voiceburst"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.datastore)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.proto)
            implementation(projects.core.repository)
            implementation(projects.core.service)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.kotlinx.collections.immutable)
        }

        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.ext.junit)
        }

        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}

// ─── NESSUN externalNativeBuild ───────────────────────────────────
// Le .so prebuilt in jniLibs/ vengono incluse automaticamente da AGP.
// Il JNI wrapper è compilato da scripts/build_codec2.sh.