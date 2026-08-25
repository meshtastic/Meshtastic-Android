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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.HamName
import org.meshtastic.core.model.isUnmessageableRole
import org.meshtastic.core.model.utf8Size
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.call_sign
import org.meshtastic.core.resources.call_sign_summary
import org.meshtastic.core.resources.ham_long_name_summary
import org.meshtastic.core.resources.hardware_model
import org.meshtastic.core.resources.long_name
import org.meshtastic.core.resources.node_id
import org.meshtastic.core.resources.short_name
import org.meshtastic.core.resources.unmessageable
import org.meshtastic.core.resources.unmonitored_or_infrastructure
import org.meshtastic.core.resources.user
import org.meshtastic.core.resources.user_config
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.RegularPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.User

private const val LONG_NAME_MAX_LENGTH = 39 // long_name max_size:40
private const val SHORT_NAME_MAX_LENGTH = 4 // short_name max_size:5

internal const val USER_LONG_NAME_TEST_TAG = "user_long_name"
internal const val HAM_LONG_NAME_TEST_TAG = "ham_long_name"
internal const val USER_SHORT_NAME_TEST_TAG = "user_short_name"

@Composable
fun UserConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val userConfig = state.userConfig
    val formState = rememberConfigState(initialValue = userConfig)
    val firmwareVersion = state.metadata?.firmware_version
    val capabilities = remember(firmwareVersion) { Capabilities(firmwareVersion) }

    // Ham onboarding repurposes the long-name field as the callsign, for the local node only (iOS parity).
    val hamMode = formState.value.is_licensed && state.isLocal
    val longNameValue = if (hamMode) HamName.split(formState.value.long_name).first else formState.value.long_name
    val longNameMax = if (hamMode) HamName.MAX_CALL_SIGN_BYTES else LONG_NAME_MAX_LENGTH
    val validLongName = longNameValue.isNotBlank() && longNameValue.utf8Size() <= longNameMax
    val validShortName = formState.value.short_name.isNotBlank()
    val validNames = validLongName && validShortName

    RadioConfigScreenList(
        title = stringResource(Res.string.user),
        onBack = onBack,
        configState = formState,
        enabled = state.connected && validNames,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = viewModel::saveUserConfig,
    ) {
        item {
            TitledCard(title = stringResource(Res.string.user_config)) {
                RegularPreference(
                    title = stringResource(Res.string.node_id),
                    subtitle = formState.value.id,
                    onClick = {},
                )
                HorizontalDivider()
                UserNameFields(
                    formState = formState,
                    hamMode = hamMode,
                    enabled = state.connected,
                    isLongNameError = !validLongName,
                    isShortNameError = !validShortName,
                )
                HorizontalDivider()
                RegularPreference(
                    title = stringResource(Res.string.hardware_model),
                    subtitle = formState.value.hw_model.name,
                    onClick = {},
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.unmessageable),
                    summary = stringResource(Res.string.unmonitored_or_infrastructure),
                    checked =
                    (formState.value.is_unmessagable ?: false) ||
                        (!capabilities.canToggleUnmessageable && formState.value.role.isUnmessageableRole()),
                    enabled = formState.value.is_unmessagable != null || capabilities.canToggleUnmessageable,
                    onCheckedChange = { formState.value = formState.value.copy(is_unmessagable = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                LicensedModeSetting(
                    checked = formState.value.is_licensed,
                    enabled = state.connected,
                    signingSupported = state.metadata?.has_xeddsa,
                    onCheckedChange = { licensed ->
                        val longName = formState.value.long_name
                        // The long-name field becomes the callsign while licensed, so reshape the name for the mode
                        // being entered: one too wide to be a callsign is demoted to the ham long name rather than
                        // discarded, and abandoning onboarding does not leave a stray separator behind.
                        formState.value =
                            formState.value.copy(
                                is_licensed = licensed,
                                long_name =
                                when {
                                    !state.isLocal -> longName
                                    licensed -> HamName.forOnboarding(longName)
                                    else -> HamName.forUnlicensing(longName)
                                },
                            )
                    },
                )
            }
        }
    }
}

/**
 * The owner name fields: long name (relabelled "Call sign" during ham onboarding), the ham-only long name, and the
 * short name.
 *
 * While licensed, a node's owner long name is the `CALLSIGN//Long name` firmware composes from the two
 * [org.meshtastic.proto.HamParameters] name fields. The device only ever reports that composed name back, so the two
 * halves are split apart for editing and rejoined on every keystroke — [formState] stays the single source of truth,
 * which is what keeps Discard, the dirty check and process-death restore working unchanged.
 */
@Composable
internal fun UserNameFields(
    formState: ConfigState<User>,
    hamMode: Boolean,
    enabled: Boolean,
    isLongNameError: Boolean,
    isShortNameError: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val (callSign, hamLongName) = remember(formState.value.long_name) { HamName.split(formState.value.long_name) }
    val keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
    val keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })

    Column(modifier = Modifier.fillMaxWidth()) {
        EditTextPreference(
            title = stringResource(if (hamMode) Res.string.call_sign else Res.string.long_name),
            value = if (hamMode) callSign else formState.value.long_name,
            summary = if (hamMode) stringResource(Res.string.call_sign_summary) else null,
            maxSize = if (hamMode) HamName.MAX_CALL_SIGN_BYTES else LONG_NAME_MAX_LENGTH,
            enabled = enabled,
            isError = isLongNameError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.testTag(USER_LONG_NAME_TEST_TAG),
            onValueChanged = {
                val longName = if (hamMode) HamName.compose(it, hamLongName) else it
                formState.value = formState.value.copy(long_name = longName)
            },
        )
        if (hamMode) {
            HorizontalDivider()
            // Optional: firmware appends it to the callsign, so an empty field names the node after the callsign.
            EditTextPreference(
                title = stringResource(Res.string.long_name),
                value = hamLongName,
                summary = stringResource(Res.string.ham_long_name_summary),
                maxSize = HamName.MAX_LONG_NAME_BYTES,
                enabled = enabled,
                isError = false,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.testTag(HAM_LONG_NAME_TEST_TAG),
                onValueChanged = { formState.value = formState.value.copy(long_name = HamName.compose(callSign, it)) },
            )
        }
        HorizontalDivider()
        EditTextPreference(
            title = stringResource(Res.string.short_name),
            value = formState.value.short_name,
            maxSize = SHORT_NAME_MAX_LENGTH,
            enabled = enabled,
            isError = isShortNameError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.testTag(USER_SHORT_NAME_TEST_TAG),
            onValueChanged = { formState.value = formState.value.copy(short_name = it) },
        )
    }
}
