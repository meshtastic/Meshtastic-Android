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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.excludes
import org.meshtastic.core.model.utf8Size
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.repository.MeshBeaconRepository
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.done
import org.meshtastic.core.resources.next
import org.meshtastic.core.resources.not_now
import org.meshtastic.core.resources.radio_setup_title
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.skip
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.OnboardingBottomBar
import org.meshtastic.core.ui.viewmodel.UIViewModel
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
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    nodeManager: NodeManager = koinInject(),
    meshBeaconRepository: MeshBeaconRepository = koinInject(),
    uiViewModel: UIViewModel = koinViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    // Closes the setup when a different radio becomes the connected one. Keyed on the transport address, not the node
    // number: a 2.8 radio renumbers itself on its first region set and must keep the setup open through it.
    // The radio this setup was opened for, kept across navigation. A reconnect clears the identity before the new
    // handshake publishes it, so a setup that composes in that gap binds to the first identity it sees.
    val currentOnDone by rememberUpdatedState(onDone)
    var boundAddress by rememberSaveable { mutableStateOf(nodeManager.connectionIdentity.value?.address) }
    LaunchedEffect(nodeManager) {
        nodeManager.connectionIdentity.filterNotNull().collect {
            val bound = boundAddress
            if (bound == null) {
                boundAddress = it.address
            } else if (it.address != bound) {
                currentOnDone()
            }
        }
    }
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val capabilities = remember(state.metadata?.firmware_version) { Capabilities(state.metadata?.firmware_version) }
    val offersBeacons = capabilities.supportsMeshBeacon && !state.metadata.excludes(ExcludedModules.MESHBEACON_CONFIG)
    // Fixed once the node is known: a fresh session starts unlicensed until its node arrives, and a licensing change
    // mid-flow must not move the page out from under an edit. The firmware's module set is known from the same node.
    val licensed = destNode?.user?.is_licensed
    val steps = remember(licensed == null) { radioSetupSteps(licensed == true, offersBeacons) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val advance = { stepIndex = (stepIndex + 1).coerceAtMost(steps.lastIndex) }
    val canStepBack = stepIndex > 0 && step != RadioSetupStep.DONE
    // The top bar's arrow and the system back do the same thing: a page back, or out from the first page.
    val stepBack = { if (canStepBack) stepIndex -= 1 else onDone() }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = canStepBack,
        onBackCompleted = { stepIndex -= 1 },
    )

    val storedLora = state.radioConfig.lora ?: Config.LoRaConfig.Builder().build()
    val loraForm = rememberConfigState(initialValue = storedLora)
    val userForm = rememberConfigState(initialValue = destNode?.user ?: User.Builder().build())
    val offersStatus =
        capabilities.supportsStatusMessage && !state.metadata.excludes(ExcludedModules.STATUSMESSAGE_CONFIG)
    val storedStatus = state.moduleConfig.statusmessage?.node_status ?: destNode?.nodeStatus.orEmpty()
    var statusInput by rememberSaveable(storedStatus) { mutableStateOf(storedStatus) }
    val beaconConfig = state.moduleConfig.mesh_beacon
    val allOffers by meshBeaconRepository.offers.collectAsStateWithLifecycle()
    val offers =
        remember(allOffers, state.radioConfig.lora, state.channelList) {
            setupBeaconOffers(allOffers, state.radioConfig.lora, state.channelList)
        }

    val responseState = state.responseState
    val saving = responseState is ResponseState.Loading
    // Two writes leave the name page; a failure on either stands until the user saves again, so a late ack for the
    // other cannot report the page saved.
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    if (responseState is ResponseState.Error) saveFailed = true
    val succeeded = responseState is ResponseState.Success && !saveFailed

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.radio_setup_title),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = stepBack,
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
                    saveFailed = false
                    if (loraSetupChanged(storedLora, loraForm.value)) {
                        val lora = loraForm.value.withCodingRateOverrideFor(capabilities)
                        viewModel.setConfig(Config.Builder().also { wb -> wb.lora = lora }.build())
                    } else {
                        advance()
                    }
                },
                onSaveName = {
                    saveFailed = false
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
                // The flow has a known length, so the page shows where it is in it.
                val progress = (stepIndex + 1).toFloat() / steps.size
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
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

                    RadioSetupStep.BEACONS ->
                        BeaconsPage(
                            beaconConfig = beaconConfig,
                            offers = offers,
                            lora = state.radioConfig.lora,
                            state = state,
                            onListenChange = { listen ->
                                beaconConfig?.let {
                                    viewModel.setModuleConfig(
                                        ModuleConfig.Builder().also { wb -> wb.mesh_beacon = it.withListen(listen) }.build(),
                                    )
                                }
                            },
                            onJoin = uiViewModel::setRequestChannelSet,
                            onNavigate = onNavigate,
                        )

                    // The summary reads back what the radio holds, not what a skipped page left in its form.
                    RadioSetupStep.DONE ->
                        DonePage(
                            lora = storedLora,
                            user = destNode?.user ?: userForm.value,
                            status = storedStatus,
                            offersStatus = offersStatus,
                            listens = beaconConfig?.listens?.takeIf { offersBeacons },
                        )
                }
            }
            LoadingOverlay(state = responseState)
            if (responseState is ResponseState.Success || responseState is ResponseState.Error) {
                PacketResponseStateDialog(
                    state = responseState,
                    onDismiss = viewModel::clearPacketResponse,
                    onBack = { if (succeeded && step != RadioSetupStep.BEACONS) advance() },
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
    when (step) {
        RadioSetupStep.INTRO ->
            OnboardingBottomBar(
                primaryText = stringResource(Res.string.next),
                onPrimary = onNext,
                secondaryText = stringResource(Res.string.not_now),
                onSecondary = onNotNow,
            )

        RadioSetupStep.REGION ->
            OnboardingBottomBar(
                primaryText = stringResource(Res.string.next),
                onPrimary = onNext,
                primaryEnabled = loraValid,
            )

        RadioSetupStep.PRESET ->
            OnboardingBottomBar(
                primaryText = stringResource(Res.string.save),
                onPrimary = onSaveLora,
                primaryEnabled = state.connected && loraValid && !saving,
            )

        RadioSetupStep.NAME ->
            OnboardingBottomBar(
                primaryText = stringResource(Res.string.save),
                onPrimary = onSaveName,
                primaryEnabled = state.connected && userValid && !saving,
                secondaryText = stringResource(Res.string.skip),
                onSecondary = onNext,
            )

        RadioSetupStep.BEACONS -> OnboardingBottomBar(primaryText = stringResource(Res.string.next), onPrimary = onNext)

        RadioSetupStep.DONE -> OnboardingBottomBar(primaryText = stringResource(Res.string.done), onPrimary = onDone)
    }
}
