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
@file:Suppress("PreviewPublic")

package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.AppTheme

@PreviewLightDark
@Composable
fun PacketAuthenticityCompatiblePreview() {
    PacketAuthenticitySettingPreview(PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_COMPATIBLE)
}

@PreviewLightDark
@Composable
fun PacketAuthenticityBalancedPreview() {
    PacketAuthenticitySettingPreview(PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED)
}

@PreviewLightDark
@Composable
fun PacketAuthenticityStrictPreview() {
    PacketAuthenticitySettingPreview(PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT)
}

@PreviewLightDark
@Composable
fun PacketAuthenticityUnsupportedPreview() {
    AppTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            PacketAuthenticitySetting(
                selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
                connected = true,
                supported = false,
                onPolicyChange = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
fun PacketAuthenticityStrictConfirmationPreview() {
    AppTheme { PacketAuthenticityStrictConfirmationDialog(show = true, onConfirm = {}, onDismiss = {}) }
}

@Composable
private fun PacketAuthenticitySettingPreview(policy: PacketSignaturePolicy) {
    AppTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            PacketAuthenticitySetting(selectedPolicy = policy, connected = true, supported = true, onPolicyChange = {})
        }
    }
}
