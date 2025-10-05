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

include(
    ":app",
    ":core:analytics",
    ":core:common",
    ":core:data",
    ":core:database",
    ":core:datastore",
    ":core:di",
    ":core:model",
    ":core:navigation",
    ":core:network",
    ":core:prefs",
    ":core:proto",
    ":core:service",
    ":core:strings",
    ":core:ui",
    ":feature:intro",
    ":feature:map",
    ":feature:node",
    ":mesh_service_example",
)
rootProject.name = "MeshtasticAndroid"

// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
    id("com.gradle.develocity") version("4.2.1")
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.4.0"
}

develocity {
    buildScan {
        capture {
            fileFingerprints.set(true)
        }
        publishing.onlyIf { false }
    }
}

@Suppress("UnstableApiUsage")
toolchainManagement {
    jvm {
        javaRepositories {
            repository("foojay") {
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
        }
    }
}