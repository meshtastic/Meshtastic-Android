/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ui.component

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import okio.ByteString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.config_security_public_key
import org.meshtastic.core.strings.encryption_error
import org.meshtastic.core.strings.encryption_error_text
import org.meshtastic.core.strings.encryption_pkc
import org.meshtastic.core.strings.encryption_pkc_text
import org.meshtastic.core.strings.encryption_psk
import org.meshtastic.core.strings.encryption_psk_text
import org.meshtastic.core.strings.error
import org.meshtastic.core.strings.security_icon_help_dismiss
import org.meshtastic.core.strings.security_icon_help_show_all
import org.meshtastic.core.strings.security_icon_help_show_less
import org.meshtastic.core.strings.show_all_key_title
import org.meshtastic.core.ui.icon.KeyOff
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.LockOpen
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

/**
 * function to display information about the current node's encryption key.
 *
 * @property hasPKC boolean if the node has public key encryption
 * @property mismatchKey boolean if the public key does not match the recorded key.
 * @property publicKey boolean if the node has a shared public key.
 */
@Composable
fun NodeKeyStatusIcon(
    modifier: Modifier = Modifier,
    hasPKC: Boolean,
    mismatchKey: Boolean,
    publicKey: ByteString? = null,
) {
    var showEncryptionDialog by remember { mutableStateOf(false) }
    if (showEncryptionDialog) {
        val (title, text) =
            when {
                mismatchKey -> Res.string.encryption_error to Res.string.encryption_error_text
                hasPKC -> Res.string.encryption_pkc to Res.string.encryption_pkc_text
                else -> Res.string.encryption_psk to Res.string.encryption_psk_text
            }
        KeyStatusDialog(title, text, publicKey) { showEncryptionDialog = false }
    }

    val (icon, tint) =
        when {
            mismatchKey -> MeshtasticIcons.KeyOff to colorScheme.StatusRed
            hasPKC -> MeshtasticIcons.Lock to colorScheme.StatusGreen
            else -> MeshtasticIcons.LockOpen to colorScheme.StatusYellow
        }

    IconButton(onClick = { showEncryptionDialog = true }, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription =
            stringResource(
                when {
                    mismatchKey -> Res.string.encryption_error
                    hasPKC -> Res.string.encryption_pkc
                    else -> Res.string.encryption_psk
                },
            ),
            tint = tint,
        )
    }
}

/**
 * Represents the various visual states of the node key as an enum. Each enum constant encapsulates the icon, color,
 * descriptive text, and optional badge details.
 *
 * @property icon The primary vector graphic for the icon.
 * @property color The tint color for the primary icon.
 * @property descriptionResId The string resource ID for the accessibility description of the icon's state.
 * @property helpTextResId The string resource ID for the detailed help text associated with this state.
 * @property title The string resource ID for the title associated with this state.
 */
@Immutable
enum class NodeKeySecurityState(
    @Stable val icon: ImageVector,
    @Stable val color: @Composable () -> Color,
    val descriptionResId: StringResource,
    val helpTextResId: StringResource,
    @Stable val title: StringResource,
) {
    // State for public key mismatch
    PKM(
        icon = MeshtasticIcons.KeyOff,
        color = { colorScheme.StatusRed },
        descriptionResId = Res.string.encryption_error,
        helpTextResId = Res.string.encryption_error_text,
        title = Res.string.encryption_error,
    ),

    // State for public key encryption
    PKC(
        icon = MeshtasticIcons.Lock,
        color = { colorScheme.StatusGreen },
        title = Res.string.encryption_pkc,
        helpTextResId = Res.string.encryption_pkc_text,
        descriptionResId = Res.string.encryption_pkc,
    ),

    // State for shared key encryption
    PSK(
        icon = MeshtasticIcons.LockOpen,
        color = { colorScheme.StatusYellow },
        title = Res.string.encryption_psk,
        helpTextResId = Res.string.encryption_psk_text,
        descriptionResId = Res.string.encryption_psk,
    ),
}

@Suppress("LongMethod", "MagicNumber")
@Composable
private fun KeyStatusDialog(title: StringResource, text: StringResource, key: ByteString?, onDismiss: () -> Unit = {}) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        modifier = Modifier,
        onDismissRequest = onDismiss,
        title = {
            if (showAll) {
                Text(stringResource(Res.string.show_all_key_title))
            } else {
                Text(stringResource(title))
            }
        },
        text = {
            if (showAll) {
                AllKeyStates()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(text), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    if (key != null && (title == Res.string.encryption_pkc || title == Res.string.encryption_error)) {
                        val isMismatch = key.size == 32 && key.toByteArray().all { it == 0.toByte() }
                        val keyString =
                            if (isMismatch) {
                                stringResource(Res.string.error)
                            } else {
                                Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP)
                            }
                        Text(
                            text = stringResource(Res.string.config_security_public_key) + ":",
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                text = keyString,
                                textAlign = TextAlign.Center,
                                color = if (isMismatch) MaterialTheme.colorScheme.error else Color.Unspecified,
                            )
                        }
                        if (!isMismatch) {
                            Spacer(Modifier.height(8.dp))
                            CopyIconButton(valueToCopy = keyString, modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(
                        if (showAll) {
                            stringResource(Res.string.security_icon_help_show_less)
                        } else {
                            stringResource(Res.string.security_icon_help_show_all)
                        },
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.security_icon_help_dismiss)) }
            }
        },
    )
}

/**
 * Displays a list of all possible node key states with their icons and descriptions within the help dialog. Iterates
 * over `NodeKeySecurityState.entries` which is provided by the enum class.
 */
@Composable
private fun AllKeyStates() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        NodeKeySecurityState.entries.forEach { state ->
            // Uses enum entries
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    NodeKeySecurityState.PKM -> NodeKeyStatusIcon(hasPKC = false, mismatchKey = true)

                    NodeKeySecurityState.PKC -> NodeKeyStatusIcon(hasPKC = true, mismatchKey = false)

                    else -> NodeKeyStatusIcon(hasPKC = false, mismatchKey = false)
                }

                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = stringResource(state.descriptionResId), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(state.helpTextResId), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state != NodeKeySecurityState.entries.lastOrNull()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun KeyStatusDialogErrorPreview() {
    AppTheme {
        KeyStatusDialog(title = Res.string.encryption_error, text = Res.string.encryption_error_text, key = null)
    }
}

@PreviewLightDark
@Composable
private fun KeyStatusDialogPkcPreview() {
    AppTheme {
        KeyStatusDialog(
            title = Res.string.encryption_pkc,
            text = Res.string.encryption_pkc_text,
            key = Channel.getRandomKey(),
        )
    }
}

@PreviewLightDark
@Composable
private fun KeyStatusDialogPskPreview() {
    AppTheme { KeyStatusDialog(title = Res.string.encryption_psk, text = Res.string.encryption_psk_text, key = null) }
}

@Preview
@Composable
private fun AllKeyStatusDialogPreview() {
    AppTheme { AllKeyStates() }
}
