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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.excludes
import org.meshtastic.core.model.utf8Size
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.done
import org.meshtastic.core.resources.next
import org.meshtastic.core.resources.not_now
import org.meshtastic.core.resources.radio_setup_title
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.skip
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.settings.radio.RadioConfigState
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.radio.component.LoadingOverlay
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import org.meshtastic.feature.settings.radio.component.rememberConfigState
import org.meshtastic.feature.settings.radio.component.withCodingRateOverrideFor
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.ExcludedModules
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/**
 * The guided setup for the connected radio: region, preset and name, then a summary. Every write goes through
 * [viewModel]'s local session, so a 2.8 radio that renumbers itself on its first region set is still followed.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun RadioSetupScreen(
    viewModel: RadioConfigViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    nodeManager: NodeManager = koinInject(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    // Closes the setup when a different radio becomes the connected one. Keyed on the transport address, not the node
    // number: a 2.8 radio renumbers itself on its first region set and must keep the setup open through it.
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(nodeManager) {
        val address = nodeManager.connectionIdentity.value?.address
        nodeManager.connectionIdentity.filterNotNull().collect {
            if (address != null && it.address != address) currentOnDone()
        }
    }
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val capabilities = remember(state.metadata?.firmware_version) { Capabilities(state.metadata?.firmware_version) }
    val steps = remember(state.localIsLicensed) { radioSetupSteps(state.localIsLicensed) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val advance = { stepIndex = (stepIndex + 1).coerceAtMost(steps.lastIndex) }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = stepIndex > 0 && step != RadioSetupStep.DONE,
        onBackCompleted = { stepIndex -= 1 },
    )

    val storedLora = state.radioConfig.lora ?: Config.LoRaConfig.Builder().build()
    val loraForm = rememberConfigState(initialValue = storedLora)
    val userForm = rememberConfigState(initialValue = destNode?.user ?: User.Builder().build())
    val offersStatus =
        capabilities.supportsStatusMessage && !state.metadata.excludes(ExcludedModules.STATUSMESSAGE_CONFIG)
    val storedStatus = state.moduleConfig.statusmessage?.node_status ?: destNode?.nodeStatus.orEmpty()
    var statusInput by rememberSaveable(storedStatus) { mutableStateOf(storedStatus) }

    val responseState = state.responseState
    val succeeded = responseState is ResponseState.Success
    val saving = responseState is ResponseState.Loading

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.radio_setup_title),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onDone,
                onClickChip = {},
                actions = {},
            )
        },
        bottomBar = {
            RadioSetupButtons(
                step = step,
                state = state,
                saving = saving,
                loraValid = loraForm.value.region != RegionCode.UNSET,
                userValid = userForm.value.isValidName(capabilities),
                onNotNow = onDone,
                onNext = advance,
                onSaveLora = {
                    if (loraSetupChanged(storedLora, loraForm.value)) {
                        val lora = loraForm.value.withCodingRateOverrideFor(capabilities)
                        viewModel.setConfig(Config.Builder().also { wb -> wb.lora = lora }.build())
                    } else {
                        advance()
                    }
                },
                onSaveName = {
                    val userDirty = userForm.isDirty
                    val statusDirty = offersStatus && statusInput != storedStatus
                    if (userDirty) viewModel.saveUserConfig(userForm.value)
                    if (statusDirty) viewModel.setModuleConfig(statusConfig(statusInput))
                    if (!userDirty && !statusDirty) advance()
                },
                onDone = onDone,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    RadioSetupStep.INTRO -> IntroPage()

                    RadioSetupStep.REGION -> RegionPage(loraForm, state, capabilities)

                    RadioSetupStep.PRESET -> PresetPage(loraForm, state, capabilities)

                    RadioSetupStep.NAME ->
                        NamePage(
                            userForm = userForm,
                            statusInput = statusInput,
                            offersStatus = offersStatus,
                            state = state,
                            capabilities = capabilities,
                            onStatusChange = { statusInput = it },
                        )

                    // The summary reads back what the radio holds, not what a skipped page left in its form.
                    RadioSetupStep.DONE ->
                        DonePage(storedLora, destNode?.user ?: userForm.value, storedStatus, offersStatus)
                }
            }
            LoadingOverlay(state = responseState)
            if (responseState is ResponseState.Success || responseState is ResponseState.Error) {
                PacketResponseStateDialog(
                    state = responseState,
                    onDismiss = viewModel::clearPacketResponse,
                    onBack = { if (succeeded) advance() },
                    rebootBehavior =
                    if (capabilities.appliesLoraConfigLive || step != RadioSetupStep.PRESET) {
                        RebootBehavior.NEVER
                    } else {
                        RebootBehavior.MAY_RESTART
                    },
                )
            }
        }
    }
}

private fun User.isValidName(capabilities: Capabilities): Boolean = long_name.isNotBlank() &&
    long_name.utf8Size() <= capabilities.maxLongNameBytes &&
    short_name.isNotBlank() &&
    short_name.utf8Size() <= SHORT_NAME_MAX_BYTES

private fun statusConfig(status: String): ModuleConfig = ModuleConfig.Builder()
    .also { wb ->
        wb.statusmessage = ModuleConfig.StatusMessageConfig.Builder().also { sb -> sb.node_status = status }.build()
    }
    .build()

@Composable
private fun RadioSetupButtons(
    step: RadioSetupStep,
    state: RadioConfigState,
    saving: Boolean,
    loraValid: Boolean,
    userValid: Boolean,
    onNotNow: () -> Unit,
    onNext: () -> Unit,
    onSaveLora: () -> Unit,
    onSaveName: () -> Unit,
    onDone: () -> Unit,
) {
    BottomAppBar(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)) {
        when (step) {
            RadioSetupStep.INTRO -> TextButton(onClick = onNotNow) { Text(stringResource(Res.string.not_now)) }

            RadioSetupStep.NAME -> TextButton(onClick = onNext) { Text(stringResource(Res.string.skip)) }

            RadioSetupStep.REGION,
            RadioSetupStep.PRESET,
            RadioSetupStep.DONE,
            -> Unit
        }
        Spacer(modifier = Modifier.weight(1f))
        when (step) {
            RadioSetupStep.INTRO -> Button(onClick = onNext) { Text(stringResource(Res.string.next)) }

            RadioSetupStep.REGION ->
                Button(onClick = onNext, enabled = loraValid) { Text(stringResource(Res.string.next)) }

            RadioSetupStep.PRESET ->
                Button(onClick = onSaveLora, enabled = state.connected && loraValid && !saving) {
                    Text(stringResource(Res.string.save))
                }

            RadioSetupStep.NAME ->
                Button(onClick = onSaveName, enabled = state.connected && userValid && !saving) {
                    Text(stringResource(Res.string.save))
                }

            RadioSetupStep.DONE -> Button(onClick = onDone) { Text(stringResource(Res.string.done)) }
        }
    }
}
