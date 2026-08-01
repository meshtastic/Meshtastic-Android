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
    id("com.gradle.develocity") version "4.5.0"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.8.0"
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
    id("org.meshtastic.flatpak.sources.settings") version "0.1.5"
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
    }
}

rootProject.name = "MeshtasticAndroid"

// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val isCI = System.getenv("CI") != null

develocity {
    server = "https://community.develocity.cloud"
    projectId = "meshtastic"
    buildScan {
        uploadInBackground = !isCI
        publishing.onlyIf { it.isAuthenticated }
        // File fingerprints are what let Develocity's build comparison explain a cache miss
        // down to the individual changed input. That is a CI-debugging tool, and the extra
        // scan payload is not worth paying for on every local build.
        capture { fileFingerprints = isCI }
        // community.develocity.cloud is a public OSS instance — the README badge links its
        // scan list — so no machine identity is published, from CI or a workstation.
        //
        // The workstation case is the live one: without this, every local build would publish
        // a contributor's OS username and machine hostname. The CI case is a hedge. Every
        // runner today is GitHub-hosted, so the raw hostname is an ephemeral Azure VM name
        // that leaks nothing and correlates nothing (VMs are never reused). But a self-hosted
        // runner's hostname WOULD be real infrastructure, and whoever adds one will not be
        // thinking about build scans.
        //
        // Deliberately constants, not something descriptive: the scan already records
        // `operatingSystem` ("Linux 7.0.0-28-generic (amd64)") and `numberOfCpuCores`, and the
        // common-custom-user-data plugin already adds CI workflow/job/step/run values, so an
        // "informative" hostname would only duplicate them.
        //
        // The `if` must stay OUTSIDE the lambdas. Referencing `isCI` inside one captures the
        // enclosing settings script object, which the configuration cache cannot serialize
        // ("cannot serialize Gradle script object references"). Keeping each lambda a bare
        // constant keeps it capture-free.
        obfuscation {
            ipAddresses { addresses -> addresses.map { _ -> "0.0.0.0" } }
            if (isCI) {
                username { "ci" }
                hostname { "ci-runner" }
            } else {
                username { "local-dev" }
                hostname { "local-machine" }
            }
        }
    }
}

// Build Cache configuration (Develocity remote cache + local)
apply(from = "gradle/build-cache.settings.gradle")

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
    ":feature:node",
    ":feature:settings",
    ":feature:discovery",
    ":feature:docs",
    ":feature:firmware",
    ":feature:wifi-provision",
    ":feature:car",
    ":desktopApp",
    ":androidApp",
    ":core:barcode",
    ":feature:widget",
    ":screenshot-tests",
    ":docs-screenshots",
    ":baselineprofile",
)
