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
    includeBuild("build-logic/settings-plugin")
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("./offline-repository") }
    }
}

plugins {
    // Develocity + CCUD + build cache; shared with build-logic, versions from the catalog.
    id("meshtastic.develocity")
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
    // 0.1.7 fixed the Isolated Projects incompatibility (shares state via a BuildService instead of
    // gradle.extensions) that previously required gating this behind an opt-in property.
    // 0.2.0 resolves platformDependencies transitively — see the collapsed list in build.gradle.kts.
    id("org.meshtastic.flatpak.sources.settings") version "0.2.2"
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
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent { snapshotsOnly() }
        }
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
        maven { url = uri("./offline-repository") }

        // Kotlin/Wasm tooling; WasmTooling.kt stops KGP declaring these per project.
        ivy {
            url = uri("https://nodejs.org/dist")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy {
            url = uri("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout { artifact("v[revision]/yarn-v[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        // Binaryen release tags are "version_<n>"; KGP's coordinate uses the bare number as [revision].
        ivy {
            url = uri("https://github.com/WebAssembly/binaryen/releases/download")
            patternLayout { artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "MeshtasticAndroid"

// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


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
    ":core:ble",
    ":core:common",
    ":core:data",
    ":core:database",
    ":core:datastore",
    ":core:di",
    ":core:domain",
    ":core:konsist",
    ":core:model",
    ":core:navigation",
    ":core:network",
    ":core:nfc",
    ":core:prefs",
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
    ":feature:map-maplibre",
    ":feature:coverage",
    ":feature:map-terrain",
    ":feature:node",
    ":feature:settings",
    ":feature:discovery",
    ":feature:docs",
    ":feature:firmware",
    ":feature:wifi-provision",
    ":desktopApp",
    ":androidApp",
    ":core:barcode",
    ":feature:widget",
    ":feature:xr",
    ":screenshot-tests",
    ":docs-screenshots",
    ":schema-strings",
    ":baselineprofile",
    ":store-screenshots",
)

// The web app and every module's wasmJs target are opt-in (WEB_TARGET_PROPERTY in build-logic's Web.kt).
if (providers.gradleProperty("meshtastic.web").map(String::toBoolean).getOrElse(false)) include(":webApp")

