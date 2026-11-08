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
package org.meshtastic.feature.settings.radio.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.currentRegionCode
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.RegionPresetConstraint
import org.meshtastic.core.model.constraintFor
import org.meshtastic.core.model.limitToBytes
import org.meshtastic.core.model.schemaLabelRes
import org.meshtastic.core.model.suggestedRegionForCountry
import org.meshtastic.core.model.utf8Size
import org.meshtastic.core.model.util.BeaconJoinOption
import org.meshtastic.core.model.util.beaconJoinOption
import org.meshtastic.core.model.util.toJoinChannelSet
import org.meshtastic.core.navigation.DiscoveryRoute
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.config_lora_modem_preset_licensed_summary
import org.meshtastic.core.resources.disabled
import org.meshtastic.core.resources.discovery_start_scan
import org.meshtastic.core.resources.enabled
import org.meshtastic.core.resources.long_name
import org.meshtastic.core.resources.lora_preset_custom
import org.meshtastic.core.resources.mesh_beacon_invitations_title
import org.meshtastic.core.resources.mesh_beacon_listen
import org.meshtastic.core.resources.mesh_beacon_listen_summary
import org.meshtastic.core.resources.mesh_beacon_offer_add
import org.meshtastic.core.resources.mesh_beacon_offer_channel
import org.meshtastic.core.resources.mesh_beacon_offer_from_unknown
import org.meshtastic.core.resources.mesh_beacon_offer_join
import org.meshtastic.core.resources.modem_preset
import org.meshtastic.core.resources.radio_setup_beacons_body
import org.meshtastic.core.resources.radio_setup_beacons_headline
import org.meshtastic.core.resources.radio_setup_beacons_listening
import org.meshtastic.core.resources.radio_setup_beacons_none
import org.meshtastic.core.resources.radio_setup_beacons_settings
import org.meshtastic.core.resources.radio_setup_beacons_waiting
import org.meshtastic.core.resources.radio_setup_done_body
import org.meshtastic.core.resources.radio_setup_done_headline
import org.meshtastic.core.resources.radio_setup_intro_body
import org.meshtastic.core.resources.radio_setup_intro_headline
import org.meshtastic.core.resources.radio_setup_name_body
import org.meshtastic.core.resources.radio_setup_name_headline
import org.meshtastic.core.resources.radio_setup_preset_body
import org.meshtastic.core.resources.radio_setup_preset_custom
import org.meshtastic.core.resources.radio_setup_reconnecting
import org.meshtastic.core.resources.radio_setup_region_body
import org.meshtastic.core.resources.radio_setup_region_none
import org.meshtastic.core.resources.radio_setup_region_search
import org.meshtastic.core.resources.radio_setup_region_suggested
import org.meshtastic.core.resources.radio_setup_restart_notice
import org.meshtastic.core.resources.schema_lora_region
import org.meshtastic.core.resources.short_name
import org.meshtastic.core.resources.status_message
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.OnboardingNotice
import org.meshtastic.core.ui.component.OnboardingPageHeader
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.feature.settings.radio.RadioConfigState
import org.meshtastic.feature.settings.radio.component.ConfigState
import org.meshtastic.feature.settings.radio.component.StatusMessageField
import org.meshtastic.feature.settings.radio.component.UserNameFields
import org.meshtastic.feature.settings.radio.component.buildPresetItems
import org.meshtastic.feature.settings.radio.component.buildRegionItems
import org.meshtastic.feature.settings.radio.component.presetLabels
import org.meshtastic.feature.settings.radio.component.regionLabels
import org.meshtastic.feature.settings.radio.component.rememberStatusMessageFocus
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig
import org.meshtastic.proto.User

internal const val SHORT_NAME_MAX_BYTES = 4 // short_name max_size:5
private const val STATUS_MESSAGE_MAX_BYTES = 80 // status_message max_size:80

@Composable
internal fun IntroPage() {
    OnboardingPageHeader(
        headline = stringResource(Res.string.radio_setup_intro_headline),
        body = stringResource(Res.string.radio_setup_intro_body),
    )
}

@Composable
internal fun RegionPage(loraForm: ConfigState<Config.LoRaConfig>, state: RadioConfigState, capabilities: Capabilities) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val regionPresetMap = if (capabilities.supportsLoraRegionPresetMap) state.loraRegionPresetMap else null
        val suggested = remember { suggestedRegionForCountry(currentRegionCode()) }
        // A suggestion is a starting point the user confirms: it fills an UNSET picker once and is never written on its
        // own, since Next stays behind the picker and Save behind the preset page.
        var suggestionShown by rememberSaveable { mutableStateOf(false) }
        remember(loraForm) {
            if (loraForm.value.region == RegionCode.UNSET && suggested != null) {
                loraForm.value = loraForm.value.withSetupRegion(suggested, regionPresetMap)
                suggestionShown = true
            }
            true
        }
        val region = loraForm.value.region
        val constraint = remember(regionPresetMap, region) { setupConstraintFor(regionPresetMap, region) }

        OnboardingPageHeader(
            headline = stringResource(Res.string.schema_lora_region),
            body = stringResource(Res.string.radio_setup_region_body),
        )
        val labels = regionLabels()
        val regions = remember(capabilities, region, labels) { buildRegionItems(capabilities, region, labels) }
        RegionList(
            regions = regions,
            selected = region,
            onSelect = { picked ->
                suggestionShown = false
                loraForm.value = loraForm.value.withSetupRegion(picked, regionPresetMap)
            },
        )
        if (suggestionShown && region == suggested) {
            OnboardingNotice(
                icon = MeshtasticIcons.Info,
                text = stringResource(Res.string.radio_setup_region_suggested),
            )
        }
        if (constraint?.isGated(state.localIsLicensed) == true) {
            OnboardingNotice(
                icon = MeshtasticIcons.Info,
                text = stringResource(Res.string.config_lora_modem_preset_licensed_summary),
            )
        }
    }
}

internal fun setupConstraintFor(map: LoRaRegionPresetMap?, region: RegionCode): RegionPresetConstraint? =
    region.takeIf { it != RegionCode.UNSET }?.let { map.constraintFor(it) }

@Composable
internal fun PresetPage(loraForm: ConfigState<Config.LoRaConfig>, state: RadioConfigState, capabilities: Capabilities) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val regionPresetMap = if (capabilities.supportsLoraRegionPresetMap) state.loraRegionPresetMap else null
        val region = loraForm.value.region
        val constraint = remember(regionPresetMap, region) { setupConstraintFor(regionPresetMap, region) }
        val presetsGated = constraint?.isGated(state.localIsLicensed) == true
        val labels = presetLabels()
        val items =
            remember(
                constraint,
                presetsGated,
                loraForm.value.modem_preset,
                capabilities,
                labels,
                regionPresetMap,
                region,
            ) {
                buildPresetItems(
                    presetConstraint = constraint,
                    presetsGated = presetsGated,
                    selectedPreset = loraForm.value.modem_preset,
                    capabilities = capabilities,
                    labels = labels,
                    unmappedRegion = region.takeIf { regionPresetMap == null },
                )
            }

        OnboardingPageHeader(
            headline = stringResource(Res.string.modem_preset),
            body = stringResource(Res.string.radio_setup_preset_body),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            DropDownPreference(
                title = stringResource(Res.string.modem_preset),
                enabled = true,
                items = items,
                selectedItem = loraForm.value.modem_preset,
                onItemSelected = { loraForm.value = loraForm.value.withSetupPreset(it) },
                summary =
                if (presetsGated) stringResource(Res.string.config_lora_modem_preset_licensed_summary) else null,
            )
        }
        if (!loraForm.value.use_preset) {
            OnboardingNotice(icon = MeshtasticIcons.Info, text = stringResource(Res.string.radio_setup_preset_custom))
        }
        if (!capabilities.appliesLoraConfigLive) {
            OnboardingNotice(icon = MeshtasticIcons.Info, text = stringResource(Res.string.radio_setup_restart_notice))
        }
        if (!state.connected) {
            OnboardingNotice(icon = MeshtasticIcons.Info, text = stringResource(Res.string.radio_setup_reconnecting))
        }
    }
}

@Composable
internal fun NamePage(
    userForm: ConfigState<User>,
    statusInput: String,
    offersStatus: Boolean,
    state: RadioConfigState,
    capabilities: Capabilities,
    onStatusChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val user = userForm.value
        OnboardingPageHeader(
            headline = stringResource(Res.string.radio_setup_name_headline),
            body = stringResource(Res.string.radio_setup_name_body),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            UserNameFields(
                formState = userForm,
                hamMode = false,
                longNameMaxBytes = capabilities.maxLongNameBytes,
                enabled = state.connected,
                isLongNameError = user.long_name.isBlank() || user.long_name.utf8Size() > capabilities.maxLongNameBytes,
                isShortNameError = user.short_name.isBlank() || user.short_name.utf8Size() > SHORT_NAME_MAX_BYTES,
            )
            if (offersStatus) {
                HorizontalDivider()
                StatusMessageField(
                    value = statusInput,
                    enabled = state.connected,
                    focus =
                    rememberStatusMessageFocus(requested = false, supported = true, connected = state.connected),
                    onValueChange = { onStatusChange(it.limitToBytes(STATUS_MESSAGE_MAX_BYTES)) },
                )
            }
        }
        if (!state.connected) {
            OnboardingNotice(icon = MeshtasticIcons.Info, text = stringResource(Res.string.radio_setup_reconnecting))
        }
    }
}

/** The region picker: a search field over a radio list, since the table is far past what a menu holds. */
@Composable
internal fun RegionList(
    regions: List<Pair<RegionCode, String>>,
    selected: RegionCode,
    onSelect: (RegionCode) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(regions, query) { regions.filter { it.second.contains(query, ignoreCase = true) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(MeshtasticIcons.Search, contentDescription = null) },
            placeholder = { Text(stringResource(Res.string.radio_setup_region_search)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            if (shown.isEmpty()) {
                Text(
                    text = stringResource(Res.string.radio_setup_region_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Column(modifier = Modifier.selectableGroup()) {
                shown.forEach { (code, label) ->
                    Row(
                        modifier =
                        Modifier.fillMaxWidth()
                            .selectable(selected = code == selected, role = Role.RadioButton) { onSelect(code) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = code == selected, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Nearby Meshes (2.8): the Listen switch writes the stored beacon config with only its listen flag changed, the heard
 * beacons join through the same dialog as discovery, and the links lead to a scan and to the beacon settings.
 */
@Composable
internal fun BeaconsPage(
    beaconConfig: MeshBeaconConfig?,
    offers: List<MeshBeaconOffer>,
    lora: Config.LoRaConfig?,
    state: RadioConfigState,
    canWrite: Boolean,
    onListenChange: (Boolean) -> Unit,
    onJoin: (ChannelSet) -> Unit,
    onNavigate: (Route) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingPageHeader(
            headline = stringResource(Res.string.radio_setup_beacons_headline),
            body = stringResource(Res.string.radio_setup_beacons_body),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(Res.string.mesh_beacon_listen),
                summary = stringResource(Res.string.mesh_beacon_listen_summary),
                checked = beaconConfig?.listens == true,
                enabled = beaconConfig != null && canWrite && !state.responseState.isWaiting(),
                onCheckedChange = onListenChange,
                containerColor = CardDefaults.cardColors().containerColor,
            )
        }
        if (beaconConfig == null) {
            OnboardingNotice(icon = MeshtasticIcons.Info, text = stringResource(Res.string.radio_setup_beacons_waiting))
        }
        Text(
            text = stringResource(Res.string.mesh_beacon_invitations_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (offers.isEmpty()) {
            Text(
                text = stringResource(Res.string.radio_setup_beacons_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        offers.forEach { offer ->
            BeaconOfferRow(
                offer = offer,
                lora = lora,
                channels = state.channelList,
                canWrite = canWrite,
                onJoin = onJoin,
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                text = stringResource(Res.string.discovery_start_scan),
                leadingIcon = MeshtasticIcons.Search,
                onClick = { onNavigate(DiscoveryRoute.DiscoveryScan) },
            )
            HorizontalDivider()
            ListItem(
                text = stringResource(Res.string.radio_setup_beacons_settings),
                leadingIcon = MeshtasticIcons.Settings,
                onClick = { onNavigate(SettingsRoute.MeshBeacon) },
            )
        }
    }
}

@Composable
private fun BeaconOfferRow(
    offer: MeshBeaconOffer,
    lora: Config.LoRaConfig?,
    channels: List<ChannelSettings>,
    canWrite: Boolean,
    onJoin: (ChannelSet) -> Unit,
) {
    val joinOption = remember(offer, lora, channels) { offer.beacon.beaconJoinOption(lora, channels) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = offer.message.ifBlank { stringResource(Res.string.mesh_beacon_offer_from_unknown) },
                    style = MaterialTheme.typography.bodyMedium,
                )
                offer.channelName?.let {
                    Text(
                        text = stringResource(Res.string.mesh_beacon_offer_channel, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Add merges the channel into a free slot with no reboot; Join retunes the radio. NONE has no channel.
            val label =
                if (joinOption == BeaconJoinOption.ADD) {
                    Res.string.mesh_beacon_offer_add
                } else {
                    Res.string.mesh_beacon_offer_join
                }
            Button(
                onClick = { offer.beacon.toJoinChannelSet(joinOption, lora)?.let(onJoin) },
                enabled = canWrite && joinOption != BeaconJoinOption.NONE,
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
internal fun DonePage(
    lora: Config.LoRaConfig,
    user: User,
    status: String,
    offersStatus: Boolean,
    listens: Boolean? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OnboardingPageHeader(
            headline = stringResource(Res.string.radio_setup_done_headline),
            body = stringResource(Res.string.radio_setup_done_body),
        )
        val regionLabel = lora.region.schemaLabelRes()?.let { stringResource(it) } ?: lora.region.name
        val presetLabel =
            if (lora.use_preset) {
                lora.modem_preset.schemaLabelRes()?.let { stringResource(it) } ?: lora.modem_preset.name
            } else {
                stringResource(Res.string.lora_preset_custom)
            }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow(stringResource(Res.string.schema_lora_region), regionLabel)
                SummaryRow(stringResource(Res.string.modem_preset), presetLabel)
                SummaryRow(stringResource(Res.string.long_name), user.long_name)
                SummaryRow(stringResource(Res.string.short_name), user.short_name)
                if (offersStatus && status.isNotBlank()) SummaryRow(stringResource(Res.string.status_message), status)
                listens?.let {
                    SummaryRow(
                        stringResource(Res.string.radio_setup_beacons_listening),
                        stringResource(if (it) Res.string.enabled else Res.string.disabled),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
