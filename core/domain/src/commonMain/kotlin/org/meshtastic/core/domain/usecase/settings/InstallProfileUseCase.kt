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

import org.koin.core.annotation.Single
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AdminEditScope
import org.meshtastic.core.repository.RadioController
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/** Use case for installing a device profile onto a radio. */
@Single
open class InstallProfileUseCase constructor(private val radioController: RadioController) {
    /**
     * Installs the provided [DeviceProfile] onto the radio at [destNum].
     *
     * @param destNum The destination node number.
     * @param profile The device profile to install.
     * @param currentUser The current user configuration of the destination node (to preserve names if not in profile).
     */
    open suspend operator fun invoke(destNum: Int, profile: DeviceProfile, currentUser: User?) {
        radioController.editSettings(destNum) {
            installOwner(profile, currentUser)
            installConfig(profile.config)
            installFixedPosition(profile.fixed_position)
            installModuleConfig(profile.module_config)
        }
    }

    // is_licensed is deliberately not installed here: enabling ham mode is a dedicated onboarding flow
    // (set_ham_mode — rewrites the owner, disables encryption, applies tx power/frequency) that a plain
    // set_owner would bypass, leaving the radio flagged licensed without those required side effects.
    private suspend fun AdminEditScope.installOwner(profile: DeviceProfile, currentUser: User?) {
        if (profile.long_name != null || profile.short_name != null || profile.is_unmessagable != null) {
            currentUser?.let {
                setOwner(
                    it.copy(
                        long_name = profile.long_name ?: it.long_name,
                        short_name = profile.short_name ?: it.short_name,
                        is_unmessagable = profile.is_unmessagable ?: it.is_unmessagable,
                    ),
                )
            }
        }
    }

    private suspend fun AdminEditScope.installConfig(config: LocalConfig?) {
        config?.let { lc ->
            lc.device?.let { setConfig(Config(device = it)) }
            lc.position?.let { setConfig(Config(position = it)) }
            lc.power?.let { setConfig(Config(power = it)) }
            lc.network?.let { setConfig(Config(network = it)) }
            lc.display?.let { setConfig(Config(display = it)) }
            lc.lora?.let { setConfig(Config(lora = it)) }
            lc.bluetooth?.let { setConfig(Config(bluetooth = it)) }
            lc.security?.let { setConfig(Config(security = it)) }
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
        lmc.mqtt?.let { setModuleConfig(ModuleConfig(mqtt = it)) }
        lmc.serial?.let { setModuleConfig(ModuleConfig(serial = it)) }
        lmc.external_notification?.let { setModuleConfig(ModuleConfig(external_notification = it)) }
        lmc.store_forward?.let { setModuleConfig(ModuleConfig(store_forward = it)) }
        lmc.range_test?.let { setModuleConfig(ModuleConfig(range_test = it)) }
        lmc.telemetry?.let { setModuleConfig(ModuleConfig(telemetry = it)) }
        lmc.canned_message?.let { setModuleConfig(ModuleConfig(canned_message = it)) }
        lmc.audio?.let { setModuleConfig(ModuleConfig(audio = it)) }
    }

    private suspend fun AdminEditScope.installModuleConfigPart2(lmc: LocalModuleConfig) {
        lmc.remote_hardware?.let { setModuleConfig(ModuleConfig(remote_hardware = it)) }
        lmc.neighbor_info?.let { setModuleConfig(ModuleConfig(neighbor_info = it)) }
        lmc.ambient_lighting?.let { setModuleConfig(ModuleConfig(ambient_lighting = it)) }
        lmc.detection_sensor?.let { setModuleConfig(ModuleConfig(detection_sensor = it)) }
        lmc.paxcounter?.let { setModuleConfig(ModuleConfig(paxcounter = it)) }
        lmc.statusmessage?.let { setModuleConfig(ModuleConfig(statusmessage = it)) }
        lmc.tak?.let { setModuleConfig(ModuleConfig(tak = it)) }
    }
}
