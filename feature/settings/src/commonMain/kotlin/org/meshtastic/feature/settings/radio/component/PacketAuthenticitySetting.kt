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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.packet_authenticity
import org.meshtastic.core.resources.packet_authenticity_balanced
import org.meshtastic.core.resources.packet_authenticity_balanced_summary
import org.meshtastic.core.resources.packet_authenticity_compatible
import org.meshtastic.core.resources.packet_authenticity_compatible_summary
import org.meshtastic.core.resources.packet_authenticity_level
import org.meshtastic.core.resources.packet_authenticity_strict
import org.meshtastic.core.resources.packet_authenticity_strict_confirm
import org.meshtastic.core.resources.packet_authenticity_strict_confirmation
import org.meshtastic.core.resources.packet_authenticity_strict_summary
import org.meshtastic.core.resources.packet_authenticity_strict_title
import org.meshtastic.core.resources.packet_authenticity_unsupported
import org.meshtastic.core.ui.component.DropDownItem
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.MeshtasticResourceDialog
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.proto.Config

internal typealias PacketSignaturePolicy = Config.SecurityConfig.PacketSignaturePolicy

internal const val PACKET_AUTHENTICITY_SELECTOR_TEST_TAG = "packet_authenticity_selector"
internal const val PACKET_AUTHENTICITY_STRICT_POLICY_TEST_TAG = "packet_authenticity_policy_strict"

@Composable
internal fun PacketAuthenticitySetting(
    selectedPolicy: PacketSignaturePolicy,
    connected: Boolean,
    supported: Boolean?,
    onPolicyChange: (PacketSignaturePolicy) -> Unit,
) {
    var showStrictConfirmation by rememberSaveable { mutableStateOf(false) }
    val canConfigurePolicy = connected && supported == true

    LaunchedEffect(canConfigurePolicy) {
        if (!canConfigurePolicy) {
            showStrictConfirmation = false
        }
    }

    PacketAuthenticityStrictConfirmationDialog(
        show = showStrictConfirmation,
        onConfirm = {
            showStrictConfirmation = false
            if (canConfigurePolicy) {
                onPolicyChange(PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT)
            }
        },
        onDismiss = { showStrictConfirmation = false },
    )

    val items =
        packetSignaturePolicies.map { policy ->
            DropDownItem(value = policy, label = stringResource(policy.labelResource()), testTag = policy.testTag())
        }
    val summaryResource =
        if (supported == false) {
            Res.string.packet_authenticity_unsupported
        } else {
            selectedPolicy.summaryResource()
        }

    TitledCard(title = stringResource(Res.string.packet_authenticity)) {
        DropDownPreference(
            title = stringResource(Res.string.packet_authenticity_level),
            enabled = canConfigurePolicy,
            items = items,
            selectedItem = selectedPolicy,
            modifier =
            Modifier.testTag(PACKET_AUTHENTICITY_SELECTOR_TEST_TAG).semantics {
                if (!canConfigurePolicy) disabled()
            },
            summary = stringResource(summaryResource),
            onItemSelected = { policy ->
                if (
                    policy == PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT &&
                    selectedPolicy != PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT
                ) {
                    showStrictConfirmation = true
                } else {
                    onPolicyChange(policy)
                }
            },
        )
    }
}

@Composable
internal fun PacketAuthenticityStrictConfirmationDialog(show: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (show) {
        MeshtasticResourceDialog(
            titleRes = Res.string.packet_authenticity_strict_title,
            messageRes = Res.string.packet_authenticity_strict_confirmation,
            confirmTextRes = Res.string.packet_authenticity_strict_confirm,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

private val packetSignaturePolicies =
    listOf(
        PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_COMPATIBLE,
        PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
        PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT,
    )

private fun PacketSignaturePolicy.labelResource(): StringResource = when (this) {
    PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_COMPATIBLE -> Res.string.packet_authenticity_compatible
    PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED -> Res.string.packet_authenticity_balanced
    PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT -> Res.string.packet_authenticity_strict
}

private fun PacketSignaturePolicy.summaryResource(): StringResource = when (this) {
    PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_COMPATIBLE -> Res.string.packet_authenticity_compatible_summary
    PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED -> Res.string.packet_authenticity_balanced_summary
    PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT -> Res.string.packet_authenticity_strict_summary
}

private fun PacketSignaturePolicy.testTag(): String? = when (this) {
    PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT -> PACKET_AUTHENTICITY_STRICT_POLICY_TEST_TAG
    else -> null
}
