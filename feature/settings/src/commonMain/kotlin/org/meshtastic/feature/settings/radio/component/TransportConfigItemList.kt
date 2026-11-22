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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.schemaLabelRes
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.config_network_udp_enabled_summary
import org.meshtastic.core.resources.schema_transport_enabled
import org.meshtastic.core.resources.transports
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.feature.settings.search.SettingsSearchCatalog
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.TransportConfig.Transport

/** One switch per transport the node lists in `supported_transports`, saved as the transport section alone. */
@Composable
fun TransportConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val transportConfig = state.radioConfig.transport ?: Config.TransportConfig.Builder().build()
    val formState = rememberConfigState(initialValue = transportConfig)
    val supported = state.metadata?.supported_transports.orEmpty()

    RadioConfigScreenList(
        modifier = modifier,
        rebootBehavior = RebootBehavior.MAY_RESTART,
        title = stringResource(Res.string.transports),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        showConnectPrompt = state.connectPromptVisible,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = { viewModel.setConfig(Config.Builder().also { wb -> wb.transport = it }.build()) },
    ) {
        fieldItem(SettingsSearchCatalog.fieldKeys(ConfigRoute.TRANSPORTS.route)) {
            TitledCard(title = stringResource(Res.string.schema_transport_enabled)) {
                val switches = transportSwitches(supported, formState.value.enabled)
                switches.forEachIndexed { index, transportSwitch ->
                    val transport = transportSwitch.transport
                    SwitchPreference(
                        title = transport.schemaLabelRes()?.let { stringResource(it) } ?: transport.name,
                        modifier = Modifier.settingsField(Res.string.schema_transport_enabled),
                        summary =
                        if (transport == Transport.TRANSPORT_MULTICAST_UDP) {
                            stringResource(Res.string.config_network_udp_enabled_summary)
                        } else {
                            ""
                        },
                        checked = transportSwitch.checked,
                        enabled = state.connected,
                        onCheckedChange = { on ->
                            val enabled = toggleTransport(formState.value.enabled, transport, on)
                            formState.value = formState.value.newBuilder().also { wb -> wb.enabled = enabled }.build()
                        },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                    if (index < switches.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
