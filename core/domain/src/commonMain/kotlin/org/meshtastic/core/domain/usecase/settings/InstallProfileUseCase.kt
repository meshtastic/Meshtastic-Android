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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.util.ChannelReplacementPlan
import org.meshtastic.core.model.util.MalformedMeshtasticUrlException
import org.meshtastic.core.model.util.toChannelReplacementPlan
import org.meshtastic.core.model.util.toChannelSet
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/** Use case for installing a device profile onto a radio. */
@Single
open class InstallProfileUseCase
constructor(
    private val radioController: RadioController,
    private val radioConfigRepository: RadioConfigRepository,
) {
    /**
     * Installs the provided [DeviceProfile] onto the radio at [destNum].
     *
     * @param destNum The destination node number.
     * @param profile The device profile to install.
     * @param currentUser The current user configuration of the destination node (to preserve names if not in profile).
     * @throws org.meshtastic.core.repository.PacketQueueRejectedException when any profile write is refused locally.
     * @throws org.meshtastic.core.repository.EditSettingsTransactionException when the begin or commit boundary is
     *   refused locally.
     * @throws org.meshtastic.core.repository.LocalNodeUnavailableException when a fixed-position profile is installed
     *   before the local node identity is available.
     * @throws MalformedMeshtasticUrlException when the profile channel URL or channel set is invalid. This is raised
     *   before the edit transaction opens, so no partial profile is applied.
     */
    open suspend operator fun invoke(
        destNum: Int,
        profile: DeviceProfile,
        currentUser: User?,
        currentLoraConfig: Config.LoRaConfig?,
        isLocal: Boolean,
    ) {
        // Decode and validate before opening the radio transaction. A malformed channel URL must not leave an edit
        // session open or allow the rest of the profile to be partially applied.
        val channelSet = profile.channel_url?.takeIf { it.isNotBlank() }?.let(::parseProfileChannelSet)
        val desiredLoraConfig = channelSet?.lora_config ?: profile.config?.lora
        val replacementPlan =
            channelSet?.let {
                try {
                    it.toChannelReplacementPlan(
                        currentSettings = emptyList(),
                        fallbackLoraConfig = profile.config?.lora ?: currentLoraConfig,
                        requirePrimary = true,
                    )
                } catch (e: IllegalArgumentException) {
                    throw MalformedMeshtasticUrlException("Invalid channel set in device profile", e)
                }
            }
        val loraConfigToWrite = desiredLoraConfig?.takeIf { it != currentLoraConfig }

        radioController.editSettings(destNum) {
            installOwner(profile, currentUser)
            installConfig(profile.config)
            installFixedPosition(profile.fixed_position)
            installModuleConfig(profile.module_config)
            installChannelsAndLora(replacementPlan, loraConfigToWrite)
        }
        if (isLocal && (replacementPlan != null || loraConfigToWrite != null)) {
            withContext(NonCancellable) {
                radioConfigRepository.updateChannelSet(
                    settingsList = replacementPlan?.normalizedSettings,
                    loraConfig = loraConfigToWrite,
                )
            }
        }
    }

    private fun parseProfileChannelSet(url: String): ChannelSet = try {
        CommonUri.parse(url).toChannelSet()
    } catch (e: IllegalArgumentException) {
        throw MalformedMeshtasticUrlException("Invalid channel URL in device profile", e)
    }

    // is_licensed is deliberately not installed here: enabling ham mode is a dedicated onboarding flow
    // (set_ham_mode — rewrites the owner, disables encryption, applies tx power/frequency) that a plain
    // set_owner would bypass, leaving the radio flagged licensed without those required side effects.
    private suspend fun AdminEditScope.installOwner(profile: DeviceProfile, currentUser: User?) {
        if (profile.long_name != null || profile.short_name != null || profile.is_unmessagable != null) {
            currentUser?.let {
                setOwner(
                    it.newBuilder()
                        .also { wb ->
                            wb.long_name = profile.long_name ?: it.long_name
                            wb.short_name = profile.short_name ?: it.short_name
                            wb.is_unmessagable = profile.is_unmessagable ?: it.is_unmessagable
                        }
                        .build(),
                )
            }
        }
    }

    private suspend fun AdminEditScope.installConfig(config: LocalConfig?) {
        config?.let { lc ->
            lc.device?.let { setConfig(Config.Builder().also { wb -> wb.device = it }.build()) }
            lc.position?.let { setConfig(Config.Builder().also { wb -> wb.position = it }.build()) }
            lc.power?.let { setConfig(Config.Builder().also { wb -> wb.power = it }.build()) }
            lc.network?.let { setConfig(Config.Builder().also { wb -> wb.network = it }.build()) }
            lc.display?.let { setConfig(Config.Builder().also { wb -> wb.display = it }.build()) }
            lc.bluetooth?.let { setConfig(Config.Builder().also { wb -> wb.bluetooth = it }.build()) }
            lc.security?.let { setConfig(Config.Builder().also { wb -> wb.security = it }.build()) }
        }
    }

    private suspend fun AdminEditScope.installFixedPosition(fixedPosition: org.meshtastic.proto.Position?) {
        if (fixedPosition != null) {
            setFixedPosition(Position(fixedPosition))
        }
    }

    private suspend fun AdminEditScope.installModuleConfig(moduleConfig: LocalModuleConfig?) {
        moduleConfig?.let { lmc ->
            installModuleConfigPart1(lmc)
            installModuleConfigPart2(lmc)
        }
    }

    private suspend fun AdminEditScope.installModuleConfigPart1(lmc: LocalModuleConfig) {
        lmc.mqtt?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.mqtt = it }.build()) }
        lmc.serial?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.serial = it }.build()) }
        lmc.external_notification?.let {
            setModuleConfig(ModuleConfig.Builder().also { wb -> wb.external_notification = it }.build())
        }
        lmc.store_forward?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.store_forward = it }.build()) }
        lmc.range_test?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.range_test = it }.build()) }
        lmc.telemetry?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.telemetry = it }.build()) }
        lmc.canned_message?.let {
            setModuleConfig(ModuleConfig.Builder().also { wb -> wb.canned_message = it }.build())
        }
        lmc.audio?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.audio = it }.build()) }
    }

    private suspend fun AdminEditScope.installModuleConfigPart2(lmc: LocalModuleConfig) {
        lmc.remote_hardware?.let {
            setModuleConfig(ModuleConfig.Builder().also { wb -> wb.remote_hardware = it }.build())
        }
        lmc.neighbor_info?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.neighbor_info = it }.build()) }
        lmc.ambient_lighting?.let {
            setModuleConfig(ModuleConfig.Builder().also { wb -> wb.ambient_lighting = it }.build())
        }
        lmc.detection_sensor?.let {
            setModuleConfig(ModuleConfig.Builder().also { wb -> wb.detection_sensor = it }.build())
        }
        lmc.paxcounter?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.paxcounter = it }.build()) }
        lmc.statusmessage?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.statusmessage = it }.build()) }
        lmc.tak?.let { setModuleConfig(ModuleConfig.Builder().also { wb -> wb.tak = it }.build()) }
    }

    private suspend fun AdminEditScope.installChannelsAndLora(
        replacementPlan: ChannelReplacementPlan?,
        loraConfig: Config.LoRaConfig?,
    ) {
        replacementPlan?.channelWrites?.forEach { setChannel(it) }
        loraConfig?.let { setConfig(Config.Builder().also { wb -> wb.lora = it }.build()) }
    }
}
