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
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.wire)
    `maven-publish`
}

apply(from = rootProject.file("gradle/publishing.gradle.kts"))

// afterEvaluate {
//     publishing {
//         publications {
//             create<MavenPublication>("release") {
//                 from(components["googleRelease"])
//                 artifactId = "core-proto"
//             }
//         }
//     }
// }

kotlin {
    jvm()
    androidLibrary {
        namespace = "org.meshtastic.core.proto"
        compileSdk = 35
        minSdk = 21
        
        compilerOptions {
             jvmTarget.set(JvmTarget.JVM_17)
        }
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
    kotlin {
    }
    root("meshtastic.*")
}


