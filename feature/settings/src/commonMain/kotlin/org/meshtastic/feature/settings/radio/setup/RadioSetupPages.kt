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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.currentRegionCode
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.RegionPresetConstraint
import org.meshtastic.core.model.constraintFor
import org.meshtastic.core.model.limitToBytes
import org.meshtastic.core.model.schemaLabelRes
import org.meshtastic.core.model.suggestedRegionForCountry
import org.meshtastic.core.model.utf8Size
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.config_lora_modem_preset_licensed_summary
import org.meshtastic.core.resources.long_name
import org.meshtastic.core.resources.lora_preset_custom
import org.meshtastic.core.resources.modem_preset
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
import org.meshtastic.core.resources.radio_setup_region_suggested
import org.meshtastic.core.resources.radio_setup_restart_notice
import org.meshtastic.core.resources.schema_lora_region
import org.meshtastic.core.resources.short_name
import org.meshtastic.core.resources.status_message
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.OnboardingNotice
import org.meshtastic.core.ui.component.OnboardingPageHeader
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.settings.radio.RadioConfigState
import org.meshtastic.feature.settings.radio.component.ConfigState
import org.meshtastic.feature.settings.radio.component.StatusMessageField
import org.meshtastic.feature.settings.radio.component.UserNameFields
import org.meshtastic.feature.settings.radio.component.buildPresetItems
import org.meshtastic.feature.settings.radio.component.buildRegionItems
import org.meshtastic.feature.settings.radio.component.presetLabels
import org.meshtastic.feature.settings.radio.component.regionLabels
import org.meshtastic.feature.settings.radio.component.rememberStatusMessageFocus
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.User

internal const val SHORT_NAME_MAX_BYTES = 4 // short_name max_size:5
private const val STATUS_MESSAGE_MAX_BYTES = 80 // status_message max_size:80

@Composable
internal fun PageHeader(headline: String, body: String) {
    OnboardingPageHeader(headline = headline, body = body)
}

@Composable
internal fun Notice(text: String) {
    OnboardingNotice(text = text, icon = MeshtasticIcons.Info)
}

@Composable
internal fun IntroPage() {
    PageHeader(
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

        PageHeader(
            headline = stringResource(Res.string.schema_lora_region),
            body = stringResource(Res.string.radio_setup_region_body),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            val labels = regionLabels()
            DropDownPreference(
                title = stringResource(Res.string.schema_lora_region),
                enabled = true,
                items = remember(capabilities, region, labels) { buildRegionItems(capabilities, region, labels) },
                selectedItem = region,
                onItemSelected = { picked ->
                    suggestionShown = false
                    loraForm.value = loraForm.value.withSetupRegion(picked, regionPresetMap)
                },
            )
        }
        if (suggestionShown && region == suggested) Notice(stringResource(Res.string.radio_setup_region_suggested))
        if (constraint?.isGated(state.localIsLicensed) == true) {
            Notice(stringResource(Res.string.config_lora_modem_preset_licensed_summary))
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

        PageHeader(
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
        if (!loraForm.value.use_preset) Notice(stringResource(Res.string.radio_setup_preset_custom))
        if (!capabilities.appliesLoraConfigLive) Notice(stringResource(Res.string.radio_setup_restart_notice))
        if (!state.connected) Notice(stringResource(Res.string.radio_setup_reconnecting))
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
        PageHeader(
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
        if (!state.connected) Notice(stringResource(Res.string.radio_setup_reconnecting))
    }
}

@Composable
internal fun DonePage(lora: Config.LoRaConfig, user: User, status: String, offersStatus: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(
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
