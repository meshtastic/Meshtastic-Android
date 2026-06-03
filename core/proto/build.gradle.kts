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
}

kotlin {
    // Override minSdk for ATAK compatibility (standard is 26)
    android { minSdk = 21 }

    sourceSets {
        commonMain.dependencies {
            api(libs.wire.runtime)

            // TAKPacket-SDK owns atak.proto Wire codegen (see
            // https://github.com/meshtastic/TAKPacket-SDK/issues/6).
            // The prune directives below stop this module from emitting
            // those classes; the SDK ships them and we re-export via api()
            // so every consumer of :core:proto gets TAKPacketV2, GeoChat,
            // etc. transitively. No dual codegen, no R8 duplicates, no
            // cross-repo ABI drift.
            //
            // Team and MemberRole are NOT pruned — they are used as fields
            // in ModuleConfig.TAKConfig and the SDK deliberately strips
            // them from its JVM JAR so our codegen is the single source.
            //
            // Excludes:
            //   zstd-jni — Android needs the @aar variant; :core:takserver
            //              re-adds it per-target.
            //   xpp3     — Android provides XmlPullParser as a platform
            //              class; :core:takserver re-adds for desktop.
            api(libs.takpacket.sdk.kmp.get().toString()) {
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
        // Upstream added packages/kmp/ with symlinks back to root protos.
        // Without filtering, Wire follows the symlinks and loads duplicates.
        include("meshtastic/**/*.proto")
        include("nanopb.proto")
        include("google/**/*.proto")
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

    // ── atak.proto types ────────────────────────────────────────────────────
    // Owned by TAKPacket-SDK (v0.2.3+), which ships the Wire-generated
    // classes in its KMP artifacts. The api() dep above re-exports them.
    // Wire prune() does not cascade to nested types — each must be explicit.
    //
    // Team and MemberRole are NOT pruned — they are used as fields in
    // ModuleConfig.TAKConfig and the SDK strips them from its JVM JAR so
    // our codegen remains the single source for those two enums.
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
    prune("meshtastic.TakTalkMessage")
    prune("meshtastic.TakTalkRoomData")
    // Marti is also shipped by the TAKPacket-SDK jar (org.meshtastic.proto.Marti),
    // so it must be pruned here too or R8 fails with a duplicate-class error at
    // release minify. (Team/MemberRole are NOT shipped by the SDK, so they stay.)
    prune("meshtastic.Marti")
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
