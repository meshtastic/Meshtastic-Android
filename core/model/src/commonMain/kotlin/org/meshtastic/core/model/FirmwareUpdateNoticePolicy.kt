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
package org.meshtastic.core.model

/** Connection transports that can determine the Android firmware-update destination. */
enum class FirmwareUpdateTransport {
    Bluetooth,
    Serial,
    Tcp,
}

/** Where a firmware-update nudge takes the user. */
enum class FirmwareUpdateDestination {
    AndroidUpdate,
    MeshtasticFlasher,
}

/** The only permitted visual treatment for a firmware update: an informational update nudge. */
enum class FirmwareUpdateNoticePresentation {
    Update,
}

/** Fully validated connected-device firmware update information for platform UI and notifications. */
data class FirmwareUpdateNotice(
    val notificationKey: String,
    val currentVersion: String,
    val stableVersion: String,
    val destination: FirmwareUpdateDestination,
    val presentation: FirmwareUpdateNoticePresentation = FirmwareUpdateNoticePresentation.Update,
)

/**
 * Determines whether a connected local device is behind the latest stable firmware and where it can be updated.
 *
 * The policy deliberately fails closed: a notice needs a known node identity, hardware target, and valid current and
 * stable versions. It must never interpret an unknown or malformed version as an update opportunity.
 */
object FirmwareUpdateNoticePolicy {
    private val VERSION_REGEX = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[.+-].*)?$")

    @Suppress("ReturnCount")
    fun createNotice(
        nodeIdentity: String?,
        currentVersion: String?,
        stableVersion: String?,
        hardware: DeviceHardware?,
        transport: FirmwareUpdateTransport,
        releaseTargets: Set<String>,
    ): FirmwareUpdateNotice? {
        val identity = nodeIdentity?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
        val deviceHardware = hardware ?: return null
        val target = deviceHardware.platformioTarget.trim().takeIf { it.isNotEmpty() } ?: return null
        if (releaseTargets.none { it.equals(target, ignoreCase = true) }) return null
        val current = currentVersion?.let(::parseVersion) ?: return null
        val stable = stableVersion?.let(::parseVersion) ?: return null
        if (current >= stable) return null

        return FirmwareUpdateNotice(
            notificationKey = notificationKey(identity, target, stable.normalized),
            currentVersion = current.normalized,
            stableVersion = stable.normalized,
            destination = destinationFor(deviceHardware, transport),
        )
    }

    fun notificationKey(nodeIdentity: String, hardwareTarget: String, stableVersion: String): String {
        val normalizedStable = parseVersion(stableVersion)?.normalized ?: stableVersion.trim()
        return "firmware-update-notified:$nodeIdentity:$hardwareTarget:$normalizedStable"
    }

    fun shouldSchedule(notificationKey: String, alreadyScheduled: Set<String>): Boolean =
        notificationKey !in alreadyScheduled

    private fun destinationFor(
        hardware: DeviceHardware,
        transport: FirmwareUpdateTransport,
    ): FirmwareUpdateDestination = if (hardware.supportsAndroidUpdate(transport)) {
            FirmwareUpdateDestination.AndroidUpdate
        } else {
            FirmwareUpdateDestination.MeshtasticFlasher
        }

    private fun DeviceHardware.supportsAndroidUpdate(transport: FirmwareUpdateTransport): Boolean = when (transport) {
            FirmwareUpdateTransport.Bluetooth ->
                isEsp32Arc || architecture.contains("nrf", ignoreCase = true)

            FirmwareUpdateTransport.Serial ->
                !isEsp32Arc &&
                    (
                        architecture.contains("nrf", ignoreCase = true) ||
                            architecture.contains("rp2040", ignoreCase = true)
                    )

            FirmwareUpdateTransport.Tcp -> isEsp32Arc
        }

    @Suppress("ReturnCount")
    private fun parseVersion(value: String): ParsedVersion? {
        val match = VERSION_REGEX.matchEntire(value.trim()) ?: return null
        val (major, minor, patch) = match.destructured
        return ParsedVersion(
            major = major.toIntOrNull() ?: return null,
            minor = minor.toIntOrNull() ?: return null,
            patch = patch.toIntOrNull() ?: return null,
        )
    }

    private data class ParsedVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ParsedVersion> {
        val normalized: String = "$major.$minor.$patch"

        override fun compareTo(other: ParsedVersion): Int =
            compareValuesBy(this, other, ParsedVersion::major, ParsedVersion::minor, ParsedVersion::patch)
    }
}
