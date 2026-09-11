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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import okio.ByteString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSecurityIndicator
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.config_security_public_key
import org.meshtastic.core.resources.encryption_error
import org.meshtastic.core.resources.encryption_error_text
import org.meshtastic.core.resources.encryption_pkc
import org.meshtastic.core.resources.encryption_pkc_text
import org.meshtastic.core.resources.error
import org.meshtastic.core.resources.no_public_key
import org.meshtastic.core.resources.no_public_key_text
import org.meshtastic.core.resources.security
import org.meshtastic.core.resources.security_icon_help_dismiss
import org.meshtastic.core.resources.security_icon_help_show_all
import org.meshtastic.core.resources.security_icon_help_show_less
import org.meshtastic.core.resources.security_legend_any_version
import org.meshtastic.core.resources.security_legend_legacy_firmware
import org.meshtastic.core.resources.security_legend_signing_firmware
import org.meshtastic.core.resources.security_signed_node
import org.meshtastic.core.resources.security_signed_node_help
import org.meshtastic.core.resources.security_verified_contact
import org.meshtastic.core.resources.security_verified_contact_help
import org.meshtastic.core.ui.icon.KeyOff
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.LockOpen
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.core.ui.icon.Person
import org.meshtastic.core.ui.icon.ShieldCheck
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

/** Key verified in person, by exchanging contact QR codes. Stronger than [SignedNodeIcon], the over-the-mesh one. */
@Composable
fun VerifiedContactIcon(modifier: Modifier = Modifier, tint: Color = colorScheme.StatusGreen) {
    BadgedIcon(
        icon = MeshtasticIcons.Person,
        badge = MeshtasticIcons.ShieldCheck,
        contentDescription = stringResource(Res.string.security_verified_contact),
        modifier = modifier,
        tint = tint,
    )
}

/** Signature verified by the radio over the mesh. Our mesh glyph, since what it vouched for is the node itself. */
@Composable
fun SignedNodeIcon(modifier: Modifier = Modifier, tint: Color = colorScheme.StatusGreen) {
    BadgedIcon(
        icon = MeshtasticIcons.Nodes,
        badge = MeshtasticIcons.ShieldCheck,
        contentDescription = stringResource(Res.string.security_signed_node),
        modifier = modifier,
        tint = tint,
    )
}

/** The name of this state, used as both the dialog title and the icon's accessibility label. */
val NodeSecurityIndicator.title: StringResource
    get() =
        when (this) {
            NodeSecurityIndicator.KEY_MISMATCH -> Res.string.encryption_error
            NodeSecurityIndicator.VERIFIED_CONTACT -> Res.string.security_verified_contact
            NodeSecurityIndicator.SIGNED_NODE -> Res.string.security_signed_node
            NodeSecurityIndicator.PUBLIC_KEY -> Res.string.encryption_pkc
            NodeSecurityIndicator.NO_PUBLIC_KEY -> Res.string.no_public_key
        }

/** The plain-language explanation of this state, shown in the dialog and the legend. */
val NodeSecurityIndicator.helpText: StringResource
    get() =
        when (this) {
            NodeSecurityIndicator.KEY_MISMATCH -> Res.string.encryption_error_text
            NodeSecurityIndicator.VERIFIED_CONTACT -> Res.string.security_verified_contact_help
            NodeSecurityIndicator.SIGNED_NODE -> Res.string.security_signed_node_help
            NodeSecurityIndicator.PUBLIC_KEY -> Res.string.encryption_pkc_text
            NodeSecurityIndicator.NO_PUBLIC_KEY -> Res.string.no_public_key_text
        }

/**
 * Which firmware versions show this state (design#149, point 11).
 *
 * Legend only. In a per-state dialog, opened from a row that already is that state, a version caveat contradicts it.
 */
val NodeSecurityIndicator.legendFirmwareNote: StringResource
    get() =
        when (this) {
            NodeSecurityIndicator.KEY_MISMATCH,
            NodeSecurityIndicator.VERIFIED_CONTACT,
            -> Res.string.security_legend_any_version

            NodeSecurityIndicator.SIGNED_NODE -> Res.string.security_legend_signing_firmware

            NodeSecurityIndicator.PUBLIC_KEY,
            NodeSecurityIndicator.NO_PUBLIC_KEY,
            -> Res.string.security_legend_legacy_firmware
        }

/**
 * This state's glyph, drawn at whatever size [modifier] gives it.
 *
 * The accessibility label comes from [title], so a row drawing a lock can never be announced as a signed node.
 */
@Composable
fun NodeSecurityIndicator.Glyph(modifier: Modifier = Modifier) {
    when (this) {
        NodeSecurityIndicator.VERIFIED_CONTACT -> VerifiedContactIcon(modifier)

        NodeSecurityIndicator.SIGNED_NODE -> SignedNodeIcon(modifier)

        else -> {
            val (icon, tint) =
                when (this) {
                    NodeSecurityIndicator.KEY_MISMATCH -> MeshtasticIcons.KeyOff to colorScheme.StatusRed
                    NodeSecurityIndicator.PUBLIC_KEY -> MeshtasticIcons.Lock to colorScheme.StatusGreen
                    else -> MeshtasticIcons.LockOpen to colorScheme.StatusYellow
                }
            Icon(imageVector = icon, contentDescription = stringResource(title), tint = tint, modifier = modifier)
        }
    }
}

/**
 * The one security indicator a node row shows beside the name (design#149), tappable for the explanation.
 *
 * Decided once in [NodeSecurityIndicator], so no surface can contradict another. [isOwnNode] reads as verified.
 */
@Composable
fun NodeSecurityIcon(
    node: Node,
    modifier: Modifier = Modifier,
    iconSize: Dp = DEFAULT_GLYPH_SIZE,
    isOwnNode: Boolean = false,
) {
    NodeSecurityIcon(
        indicator = NodeSecurityIndicator.of(node, isOwnNode),
        modifier = modifier,
        iconSize = iconSize,
        // The stored key, same source mismatchKey reads, so a mismatch dialog shows the sentinel not the advertised
        // key.
        publicKey = node.publicKey ?: node.user.public_key,
    )
}

/** [NodeSecurityIcon] for a state resolved elsewhere — the DM thread's own header, say. */
@Composable
fun NodeSecurityIcon(
    indicator: NodeSecurityIndicator,
    modifier: Modifier = Modifier,
    iconSize: Dp = DEFAULT_GLYPH_SIZE,
    publicKey: ByteString? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        NodeSecurityDialog(indicator = indicator, key = publicKey, onDismiss = { showDialog = false })
    }
    // The button keeps its own minimum touch target; only the glyph inside it takes iconSize.
    IconButton(onClick = { showDialog = true }, modifier = modifier) { indicator.Glyph(Modifier.size(iconSize)) }
}

/**
 * The DM thread header's encryption lock.
 *
 * About this thread's encryption, not the node's identity, so it keeps the locks at every firmware version.
 */
@Composable
fun NodeKeyStatusIcon(
    hasPKC: Boolean,
    mismatchKey: Boolean,
    modifier: Modifier = Modifier,
    publicKey: ByteString? = null,
) {
    val indicator =
        NodeSecurityIndicator.resolve(firmwareVersion = null, hasPublicKey = hasPKC, mismatchKey = mismatchKey)
    NodeSecurityIcon(indicator = indicator, modifier = modifier, publicKey = publicKey)
}

/** The glyph size a row uses when it does not ask for one. */
private val DEFAULT_GLYPH_SIZE = 20.dp

/** The legend order, weakest claim last — the same reading order as the help sheet on the other clients. */
private val LEGEND_ORDER =
    listOf(
        NodeSecurityIndicator.VERIFIED_CONTACT,
        NodeSecurityIndicator.SIGNED_NODE,
        NodeSecurityIndicator.PUBLIC_KEY,
        NodeSecurityIndicator.NO_PUBLIC_KEY,
        NodeSecurityIndicator.KEY_MISMATCH,
    )

/** The explanation for one state, with a toggle to the full legend. Shared by every surface that shows a glyph. */
@Suppress("LongMethod")
@Composable
fun NodeSecurityDialog(indicator: NodeSecurityIndicator, key: ByteString?, onDismiss: () -> Unit = {}) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (showAll) Res.string.security else indicator.title)) },
        text = {
            if (showAll) {
                SecurityLegend()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(indicator.helpText), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    PublicKeyBlock(indicator, key)
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

/** The node's public key, shown only where it is the subject: a key on file, or one that stopped matching. */
@Suppress("MagicNumber")
@Composable
private fun PublicKeyBlock(indicator: NodeSecurityIndicator, key: ByteString?) {
    if (key == null) return
    if (indicator != NodeSecurityIndicator.PUBLIC_KEY && indicator != NodeSecurityIndicator.KEY_MISMATCH) return

    PublicKeyContent(key)
}

/** The key itself, with a copy affordance — or the error word when the stored key is the mismatch sentinel. */
@Suppress("MagicNumber")
@Composable
private fun PublicKeyContent(key: ByteString) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    val isMismatch = key.size == 32 && key.toByteArray().all { it == 0.toByte() }
    val keyString = if (isMismatch) stringResource(Res.string.error) else key.base64()
    Text(text = stringResource(Res.string.config_security_public_key) + ":", textAlign = TextAlign.Center)
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

/** Every state the node list can show, with the firmware each one applies to (design#149, point 11). */
@Composable
private fun SecurityLegend() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        LEGEND_ORDER.forEach { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Content, not a control: an IconButton here is focusable and activatable but does nothing.
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { state.Glyph() }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = stringResource(state.title), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(state.helpText), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(state.legendFirmwareNote),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state != LEGEND_ORDER.last()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NodeSecurityDialogMismatchPreview() {
    AppTheme { NodeSecurityDialog(indicator = NodeSecurityIndicator.KEY_MISMATCH, key = Node.ERROR_BYTE_STRING) }
}

@PreviewLightDark
@Composable
private fun NodeSecurityDialogPkcPreview() {
    AppTheme { NodeSecurityDialog(indicator = NodeSecurityIndicator.PUBLIC_KEY, key = Channel.getRandomKey()) }
}

@PreviewLightDark
@Composable
private fun NodeSecurityDialogVerifiedPreview() {
    AppTheme { NodeSecurityDialog(indicator = NodeSecurityIndicator.VERIFIED_CONTACT, key = null) }
}

@PreviewLightDark
@Composable
private fun NodeSecurityDialogSignedPreview() {
    AppTheme { NodeSecurityDialog(indicator = NodeSecurityIndicator.SIGNED_NODE, key = null) }
}

@Suppress("PreviewPublic")
@Preview
@Composable
fun SecurityLegendPreview() {
    AppTheme { SecurityLegend() }
}

/**
 * Every glyph at the two sizes the node rows actually use — 20dp complete, 18dp compact.
 *
 * The composites are drawn rather than laid out, so the badge only reads if it survives the smaller size; this is the
 * preview that shows whether it does.
 */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun NodeSecurityGlyphsPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(20.dp, 18.dp).forEach { size ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NodeSecurityIndicator.entries.forEach { it.Glyph(Modifier.size(size)) }
                }
            }
        }
    }
}
