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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConfigBundle

/**
 * POC ViewModel that reads device configuration from the SDK's [ConfigBundle] and
 * writes changes back via [org.meshtastic.sdk.AdminApi.editSettings].
 *
 * **Read path:** [RadioClient.configBundle] is a [StateFlow] cached from the handshake — zero
 * RPCs required. It contains all [Config] and [ModuleConfig] entries as they were at connect time.
 *
 * **Write path:** [editSettings] issues a single-RPC batch write. On success we apply an
 * optimistic local overlay via [_localConfigOverrides] so UI reads see the new value immediately.
 *
 * **SDK Gap G surfaced:** After a successful [AdminApi.editSettings] write, [RadioClient.configBundle]
 * is NOT automatically refreshed — it still holds the pre-write snapshot. Until the SDK emits a
 * fresh [ConfigBundle] after each write, callers must maintain their own optimistic overlay (as
 * done here). Logged as Gap G for SDK fix.
 *
 * **SDK Gap C surfaced:** [ConfigBundle] has no `channels` field; channels are only available via
 * [org.meshtastic.sdk.AdminApi.listChannels] (8 serial RPCs). Exposed via [loadChannels].
 * Logged as Gap C.
 */
@KoinViewModel
class SdkConfigViewModel(
    private val provider: RadioClientProvider,
) : ViewModel() {

    /** The raw ConfigBundle from the handshake; null until connected+configured. */
    val configBundle: StateFlow<ConfigBundle?> = provider.client
        .flatMapLatest { it?.configBundle ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Optimistic local config overrides applied after successful writes.
     * Keyed by Config type tag (e.g. "device", "lora"). Merged with [configBundle] in [deviceConfig].
     *
     * Gap G: remove this overlay once SDK emits a fresh configBundle after editSettings writes.
     */
    private val _localConfigOverrides = MutableStateFlow<Map<String, Config>>(emptyMap())
    val localConfigOverrides: StateFlow<Map<String, Config>> = _localConfigOverrides.asStateFlow()

    /** Device config — merged with any pending local override. */
    val deviceConfig: StateFlow<Config.DeviceConfig?> = configBundle
        .map { bundle ->
            // Prefer local override if present (Gap G workaround)
            _localConfigOverrides.value["device"]?.device
                ?: bundle?.configs?.firstOrNull { it.device != null }?.device
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** LoRa config — merged with any pending local override. */
    val loraConfig: StateFlow<Config.LoRaConfig?> = configBundle
        .map { bundle ->
            _localConfigOverrides.value["lora"]?.lora
                ?: bundle?.configs?.firstOrNull { it.lora != null }?.lora
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Write a config update to the radio via [AdminApi.editSettings].
     *
     * On success, applies the new config as a local optimistic override so UI sees it
     * immediately (Gap G workaround — SDK doesn't refresh configBundle after writes).
     */
    fun setConfig(config: Config, typeKey: String) {
        val client = provider.client.value ?: run {
            Logger.w { "[SDK] setConfig: no active client" }
            return
        }
        viewModelScope.launch {
            when (val result = client.admin.editSettings { setConfig(config) }) {
                is AdminResult.Success -> {
                    Logger.i { "[SDK] setConfig($typeKey) succeeded" }
                    _localConfigOverrides.update { it + (typeKey to config) }
                }
                AdminResult.Timeout ->
                    Logger.w { "[SDK] setConfig($typeKey): Timeout" }
                AdminResult.Unauthorized ->
                    Logger.w { "[SDK] setConfig($typeKey): Unauthorized" }
                AdminResult.SessionKeyExpired ->
                    Logger.w { "[SDK] setConfig($typeKey): SessionKeyExpired — reconnect needed" }
                AdminResult.NodeUnreachable ->
                    Logger.w { "[SDK] setConfig($typeKey): NodeUnreachable" }
                is AdminResult.Failed ->
                    Logger.e { "[SDK] setConfig($typeKey): Failed — ${result.routingError}" }
            }
        }
    }

    /** Convenience: update device config. */
    fun setDeviceConfig(device: Config.DeviceConfig) = setConfig(Config(device = device), "device")

    /** Convenience: update LoRa config. */
    fun setLoraConfig(lora: Config.LoRaConfig) = setConfig(Config(lora = lora), "lora")

    /**
     * Update owner name on the radio.
     *
     * Gap G: `ownNode` StateFlow on RadioClient is not refreshed after setOwner either —
     * same root cause as config writes.
     */
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
     * Load all 8 channels via serial RPCs.
     *
     * Gap C: No reactive `client.channels: StateFlow<List<Channel>>` — only 8 serial RPCs via
     * [AdminApi.listChannels]. Callers must re-request on every mount.
     * SDK fix: cache channels from storage during handshake and expose as StateFlow.
     */
    fun loadChannels(onResult: (AdminResult<List<org.meshtastic.proto.Channel>>) -> Unit) {
        val client = provider.client.value ?: return
        viewModelScope.launch {
            val result = client.admin.listChannels()
            Logger.i { "[SDK] listChannels → $result" }
            onResult(result)
        }
    }

    /**
     * Diagnostics: log the full ConfigBundle contents.
     * Useful for POC validation — call from a debug menu or LaunchedEffect.
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
