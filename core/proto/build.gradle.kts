/*
 * Copyright (c) 2025 Meshtastic LLC
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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.wire)
}

kotlin {
    targets.named("android", com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget::class.java) {
//        publishLibraryVariants("release", "debug")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        namespace = "org.meshtastic.core.proto"
        compileSdk = 36
        minSdk = 26
    }
    
    sourceSets {
        commonMain.dependencies {
            api(libs.wire.runtime)
        }
    }
}



wire {
    sourcePath {
        srcDir("src/main/proto")
        srcDir("src/main/wire-includes")
    }
    kotlin {}
}
