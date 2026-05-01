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

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
    id("com.gradle.develocity") version "4.4.1"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.6.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Only enable mavenLocal for local JitPack testing; never in CI.
        if (providers.gradleProperty("useMavenLocal").isPresent) mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "MeshtasticAndroid"

// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Shared Develocity and Build Cache configuration
apply(from = "gradle/develocity.settings.gradle")

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

include(
    ":app",
    ":core:api",
    ":core:barcode",
    ":core:ble",
    ":core:common",
    ":core:data",
    ":core:database",
    ":core:datastore",
    ":core:di",
    ":core:domain",
    ":core:model",
    ":core:navigation",
    ":core:network",
    ":core:nfc",
    ":core:prefs",
    ":core:proto",
    ":core:repository",
    ":core:service",
    ":core:resources",
    ":core:takserver",
    ":core:testing",
    ":core:ui",
    ":feature:intro",
    ":feature:messaging",
    ":feature:connections",
    ":feature:map",
    ":feature:node",
    ":feature:settings",
    ":feature:firmware",
    ":feature:wifi-provision",
    ":feature:widget",
    ":desktop",
)
