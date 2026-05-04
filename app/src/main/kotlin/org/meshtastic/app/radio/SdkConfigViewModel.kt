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
package org.meshtastic.app.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConfigBundle

/**
 * POC ViewModel that reads device configuration from the SDK's [ConfigBundle] and writes changes back via
 * [org.meshtastic.sdk.AdminApi.editSettings].
 *
 * **Read path:** [RadioClient.configBundle] is a [StateFlow] cached from the handshake — zero RPCs required. It
 * contains all [Config] and [ModuleConfig] entries as they were at connect time.
 *
 * **Write path:** [editSettings] issues a single-RPC batch write. The SDK auto-refreshes [configBundle] after a
 * successful commit (Gap G resolved).
 *
 * **Gap C resolved:** [RadioClient.channels] is now a reactive StateFlow seeded from the handshake.
 */
@KoinViewModel
class SdkConfigViewModel(private val provider: RadioClientProvider) : ViewModel() {

    /** The raw ConfigBundle from the handshake; null until connected+configured. */
    val configBundle: StateFlow<ConfigBundle?> =
        provider.client
            .flatMapLatest { it?.configBundle ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Device config — read directly from SDK configBundle (Gap G resolved, no local overlay needed). */
    val deviceConfig: StateFlow<Config.DeviceConfig?> =
        configBundle
            .map { bundle -> bundle?.configs?.firstOrNull { it.device != null }?.device }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** LoRa config — read directly from SDK configBundle. */
    val loraConfig: StateFlow<Config.LoRaConfig?> =
        configBundle
            .map { bundle -> bundle?.configs?.firstOrNull { it.lora != null }?.lora }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reactive channel list from the SDK (Gap C resolved — seeded from handshake, updated on setChannel). */
    val channels: StateFlow<List<org.meshtastic.proto.Channel>> =
        provider.client
            .flatMapLatest { client -> client?.channels?.map { it.orEmpty() } ?: flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Write a config update to the radio via [AdminApi.editSettings].
     *
     * The SDK automatically refreshes configBundle after a successful commit.
     */
    fun setConfig(config: Config, typeKey: String) {
        val client =
            provider.client.value
                ?: run {
                    Logger.w { "[SDK] setConfig: no active client" }
                    return
                }
        viewModelScope.launch {
            when (val result = client.admin.editSettings { setConfig(config) }) {
                is AdminResult.Success -> Logger.i { "[SDK] setConfig($typeKey) succeeded" }
                AdminResult.Timeout -> Logger.w { "[SDK] setConfig($typeKey): Timeout" }
                AdminResult.Unauthorized -> Logger.w { "[SDK] setConfig($typeKey): Unauthorized" }
                AdminResult.SessionKeyExpired ->
                    Logger.w { "[SDK] setConfig($typeKey): SessionKeyExpired — reconnect needed" }
                AdminResult.NodeUnreachable -> Logger.w { "[SDK] setConfig($typeKey): NodeUnreachable" }
                is AdminResult.Failed -> Logger.e { "[SDK] setConfig($typeKey): Failed — ${result.routingError}" }
            }
        }
    }

    /** Convenience: update device config. */
    fun setDeviceConfig(device: Config.DeviceConfig) = setConfig(Config(device = device), "device")

    /** Convenience: update LoRa config. */
    fun setLoraConfig(lora: Config.LoRaConfig) = setConfig(Config(lora = lora), "lora")

    /** Update owner name on the radio. */
    fun setOwner(longName: String, shortName: String) {
        val client = provider.client.value ?: return
        viewModelScope.launch {
            when (val result = client.admin.setOwner(User(long_name = longName, short_name = shortName))) {
                is AdminResult.Success -> Logger.i { "[SDK] setOwner succeeded" }
                else -> Logger.w { "[SDK] setOwner failed: $result" }
            }
        }
    }

    /**
     * Diagnostics: log the full ConfigBundle contents. Useful for POC validation — call from a debug menu or
     * LaunchedEffect.
     */
    fun logConfigBundle() {
        val bundle = configBundle.value
        if (bundle == null) {
            Logger.i { "[SDK] configBundle: null (not yet connected)" }
            return
        }
        Logger.i { "[SDK] myNodeNum=${bundle.myInfo.my_node_num}" }
        Logger.i { "[SDK] firmwareVersion=${bundle.metadata.firmware_version}" }
        bundle.configs.forEach { c -> Logger.d { "[SDK] Config: $c" } }
        bundle.moduleConfigs.forEach { mc -> Logger.d { "[SDK] ModuleConfig: $mc" } }
    }
}
