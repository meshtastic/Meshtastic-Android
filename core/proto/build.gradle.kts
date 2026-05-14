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
    alias(libs.plugins.wire)
    id("meshtastic.publishing")
    alias(libs.plugins.flatpak.gradle.generator)
}

kotlin {
    // Override minSdk for ATAK compatibility (standard is 26)
    android { minSdk = 21 }

    sourceSets {
        commonMain.dependencies {
            api(libs.wire.runtime)

            // TAKPacket-SDK owns `meshtastic/atak.proto` Wire codegen (issue
            // https://github.com/meshtastic/TAKPacket-SDK/issues/6). The
            // `prune` directives below stop this module from emitting those
            // classes; the SDK ships them and we re-export it via `api` so
            // every consumer of `:core:proto` (`:core:model`,
            // `:core:takserver`, `:feature:settings`, etc.) picks up
            // `org.meshtastic.proto.TAKPacketV2`, `Team`, `MemberRole`, and
            // the rest transitively. No second codegen, no R8 duplicates,
            // no cross-repo ABI drift the way the issue-#5 strip strategy
            // had.
            //
            // The KMP parent coord (no `-jvm` suffix) routes each consuming
            // target to the right SDK variant: `jvm()` and Android pick up
            // `takpacket-sdk-jvm`, iOS targets pick up the iOS klibs.
            //
            // Excludes:
            //   - zstd-jni JAR: Android needs the @aar variant for native
            //     `.so` files; `:core:takserver` re-adds it per-target.
            //   - xpp3: bundles `XmlPullParser` which Android provides as
            //     a platform class; would trigger R8 duplicate-class.
            //     `:core:takserver`'s jvmMain re-adds it for desktop.
            // Both excludes are no-ops on iOS (the SDK's iOS klibs use the
            // bundled CZstd C interop, not zstd-jni).
            api("org.meshtastic:takpacket-sdk:0.2.1") {
                exclude(group = "com.github.luben", module = "zstd-jni")
                exclude(group = "org.ogce", module = "xpp3")
            }
        }
    }
}

wire {
    sourcePath {
        srcDir("src/main/proto")
        srcDir("src/main/wire-includes")
    }
    kotlin {
        // Wire 6 optimization: Avoid unnecessary immutable copies of repeated/map fields.
        // Improves performance by reducing allocations when decoding/creating messages.
        makeImmutableCopies = false

        // Flattens 'oneof' fields into nullable properties on the parent class.
        // This removes the intermediate sealed classes, simplifying usage and reducing method count/binary size.
        // Codebase is already written to use the nullable properties (e.g. packet.decoded vs
        // packet.payload_variant.decoded).
        boxOneOfsMinSize = 5000
    }
    root("meshtastic.*")
    prune("meshtastic.MeshPacket#delayed")
    prune("meshtastic.MeshPacket.Delayed")

    // atak.proto types are owned by the TAKPacket-SDK (issue
    // https://github.com/meshtastic/TAKPacket-SDK/issues/6). Pruning here
    // stops Wire from emitting `org.meshtastic.proto.*` classes that the
    // SDK already ships, eliminating the R8 duplicate-class errors that
    // first surfaced in issue #5 and the cross-repo ABI drift that broke
    // the release build when `SensorFov.range_m` flipped from `uint32` to
    // `optional uint32`.
    //
    // Consuming code that imports `org.meshtastic.proto.TAKPacketV2` etc.
    // still compiles because the SDK provides those classes transitively
    // from `:core:takserver`'s commonMain dependency. `Team` and
    // `MemberRole` are pruned too, even though `module_config.proto`'s
    // ModuleConfig.TAKConfig has fields of those enum types — Wire's
    // generated `ModuleConfig.TAKConfig` adapter references the FQN
    // `org.meshtastic.proto.Team` which resolves transitively from the
    // SDK at Kotlin compile time.
    //
    // Keep this list in sync with the top-level messages and enums in
    // `meshtastic/atak.proto`. Any new top-level type added there must be
    // added here too, or the app will hit a duplicate-class error.
    prune("meshtastic.TAKPacket")
    prune("meshtastic.TAKPacketV2")
    prune("meshtastic.GeoChat")
    prune("meshtastic.GeoChat.ReceiptType")
    prune("meshtastic.Group")
    prune("meshtastic.Status")
    prune("meshtastic.Contact")
    prune("meshtastic.PLI")
    prune("meshtastic.AircraftTrack")
    prune("meshtastic.CotGeoPoint")
    prune("meshtastic.DrawnShape")
    prune("meshtastic.DrawnShape.Kind")
    prune("meshtastic.DrawnShape.StyleMode")
    prune("meshtastic.Marker")
    prune("meshtastic.Marker.Kind")
    prune("meshtastic.RangeAndBearing")
    prune("meshtastic.Route")
    prune("meshtastic.Route.Method")
    prune("meshtastic.Route.Direction")
    prune("meshtastic.Route.Link")
    prune("meshtastic.CasevacReport")
    prune("meshtastic.CasevacReport.Precedence")
    prune("meshtastic.CasevacReport.HlzMarking")
    prune("meshtastic.CasevacReport.Security")
    prune("meshtastic.ZMistEntry")
    prune("meshtastic.EmergencyAlert")
    prune("meshtastic.EmergencyAlert.Type")
    prune("meshtastic.TaskRequest")
    prune("meshtastic.TaskRequest.Priority")
    prune("meshtastic.TaskRequest.Status")
    prune("meshtastic.TAKEnvironment")
    prune("meshtastic.SensorFov")
    prune("meshtastic.SensorFov.SensorType")
    prune("meshtastic.CotHow")
    prune("meshtastic.CotType")
    prune("meshtastic.GeoPointSource")
    // `Team` and `MemberRole` are NOT pruned: module_config.proto's
    // ModuleConfig.TAKConfig has `Team team = 1` and `MemberRole role = 2`
    // fields. Wire prune is cascading — removing the enum types would
    // also remove the fields that reference them from the generated
    // ModuleConfig.TAKConfig, breaking every call site that reads
    // `takConfig.team` / `takConfig.role`. The SDK also ships these two
    // enums (it has its own copies of TAKPacketV2.team / .role) but
    // both sides generate byte-identical Kotlin from the same enum
    // definition, so R8's strict duplicate-class check is the only thing
    // we have to worry about. That's handled in the app's release build
    // config via a Wire-classpath-exclusion (see :app's R8 rules).
}

// Modern KMP publication uses the project name as the artifactId by default.
// We rename the publications to include the 'core-' prefix for consistency.
publishing {
    publications.withType<MavenPublication>().configureEach {
        val baseId = artifactId
        if (baseId == "proto") {
            artifactId = "meshtastic-android-proto"
        } else if (baseId.startsWith("proto-")) {
            artifactId = baseId.replace("proto-", "meshtastic-android-proto-")
        }
    }
}

tasks.flatpakGradleGenerator {
    outputFile = file("../../flatpak-sources-core-proto.json")
    downloadDirectory.set("./offline-repository")
    excludeConfigurations.set(listOf("testCompileClasspath", "testRuntimeClasspath"))
}
