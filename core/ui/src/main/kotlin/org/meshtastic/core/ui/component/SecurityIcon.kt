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
@file:Suppress("TooManyFunctions")

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.security_icon_badge_warning_description
import org.meshtastic.core.strings.security_icon_description
import org.meshtastic.core.strings.security_icon_help_dismiss
import org.meshtastic.core.strings.security_icon_help_green_lock
import org.meshtastic.core.strings.security_icon_help_red_open_lock
import org.meshtastic.core.strings.security_icon_help_show_all
import org.meshtastic.core.strings.security_icon_help_show_less
import org.meshtastic.core.strings.security_icon_help_title
import org.meshtastic.core.strings.security_icon_help_title_all
import org.meshtastic.core.strings.security_icon_help_warning_precise_mqtt
import org.meshtastic.core.strings.security_icon_help_yellow_open_lock
import org.meshtastic.core.strings.security_icon_insecure_no_precise
import org.meshtastic.core.strings.security_icon_insecure_precise_only
import org.meshtastic.core.strings.security_icon_secure
import org.meshtastic.core.strings.security_icon_warning_precise_mqtt
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.LockOpen
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig

private const val PRECISE_POSITION_BITS = 32

/**
 * Represents the various visual states of the security icon as an enum. Each enum constant encapsulates the icon,
 * color, descriptive text, and optional badge details.
 *
 * @property icon The primary vector graphic for the icon.
 * @property color The tint color for the primary icon.
 * @property descriptionResId The string resource ID for the accessibility description of the icon's state.
 * @property helpTextResId The string resource ID for the detailed help text associated with this state.
 * @property badgeIcon Optional vector graphic for a badge to be displayed on the icon.
 * @property badgeIconColor Optional tint color for the badge icon.
 */
@Immutable
enum class SecurityState(
    @Stable val icon: ImageVector,
    @Stable val color: @Composable () -> Color,
    val descriptionResId: StringResource,
    val helpTextResId: StringResource,
    @Stable val badgeIcon: ImageVector? = null,
    @Stable val badgeIconColor: @Composable () -> Color? = { null },
) {
    /** State for a secure channel (green lock). */
    SECURE(
        icon = MeshtasticIcons.Lock,
        color = { colorScheme.StatusGreen },
        descriptionResId = Res.string.security_icon_secure,
        helpTextResId = Res.string.security_icon_help_green_lock,
    ),

    /**
     * State for an insecure channel, not used for precise location, and MQTT not the primary concern for a higher
     * warning. (yellow open lock)
     */
    INSECURE_NO_PRECISE(
        icon = MeshtasticIcons.LockOpen,
        color = { colorScheme.StatusYellow },
        descriptionResId = Res.string.security_icon_insecure_no_precise,
        helpTextResId = Res.string.security_icon_help_yellow_open_lock,
    ),

    /**
     * State for an insecure channel with precise location enabled, but MQTT not causing the highest warning. (red open
     * lock)
     */
    INSECURE_PRECISE_ONLY(
        icon = MeshtasticIcons.LockOpen,
        color = { colorScheme.StatusRed },
        descriptionResId = Res.string.security_icon_insecure_precise_only,
        helpTextResId = Res.string.security_icon_help_red_open_lock,
    ),

    /**
     * State indicating an insecure channel with precise location and MQTT enabled (red open lock with yellow warning
     * badge).
     */
    INSECURE_PRECISE_MQTT_WARNING(
        icon = MeshtasticIcons.LockOpen,
        color = { colorScheme.StatusRed },
        descriptionResId = Res.string.security_icon_warning_precise_mqtt,
        helpTextResId = Res.string.security_icon_help_warning_precise_mqtt,
        badgeIcon = MeshtasticIcons.Warning,
        badgeIconColor = { colorScheme.StatusYellow },
    ),
}

/**
 * Internal composable to display the security icon, potentially with a badge.
 *
 * @param icon The main vector graphic for the icon.
 * @param mainIconTint The tint color for the main icon.
 * @param contentDescription The accessibility description for the icon.
 * @param modifier Modifier for this composable.
 * @param badgeIcon Optional vector graphic for the badge.
 * @param badgeIconColor Optional tint color for the badge icon.
 */
@Composable
private fun SecurityIconDisplay(
    icon: ImageVector,
    mainIconTint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    badgeIcon: ImageVector? = null,
    badgeIconColor: Color? = null,
) {
    BadgedBox(
        badge = {
            if (badgeIcon != null) {
                Badge(
                    containerColor = Color.Transparent, // Allows badgeIconColor to define appearance
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = stringResource(Res.string.security_icon_badge_warning_description),
                        tint = badgeIconColor ?: colorScheme.onError, // Default for contrast
                        modifier = Modifier.size(16.dp), // Adjusted badge icon size
                    )
                }
            }
        },
        modifier = modifier,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = mainIconTint)
    }
}

/**
 * Determines the [SecurityState] based on channel properties. The priority of states is: MQTT warning, then secure,
 * then insecure variations.
 *
 * @param isLowEntropyKey True if the channel uses a low entropy key (not securely encrypted).
 * @param isPreciseLocation True if precise location is enabled.
 * @param isMqttEnabled True if MQTT is enabled for the channel.
 * @return The determined [SecurityState].
 */
private fun determineSecurityState(
    isLowEntropyKey: Boolean,
    isPreciseLocation: Boolean,
    isMqttEnabled: Boolean,
): SecurityState = when {
    !isLowEntropyKey -> SecurityState.SECURE

    isMqttEnabled && isPreciseLocation -> SecurityState.INSECURE_PRECISE_MQTT_WARNING

    isPreciseLocation -> SecurityState.INSECURE_PRECISE_ONLY

    else -> SecurityState.INSECURE_NO_PRECISE
}

/**
 * Displays an icon representing the security status of a channel. Clicking the icon shows a detailed help dialog.
 *
 * @param securityState The current [SecurityState] to display.
 * @param baseContentDescription The base content description for the icon, to which the specific state description will
 *   be appended. Defaults to a generic security icon description.
 * @param externalOnClick Optional lambda to be invoked when the icon is clicked, in addition to its primary action
 *   (showing a help dialog). This allows callers to inject custom side effects.
 */
@Composable
fun SecurityIcon(
    securityState: SecurityState,
    baseContentDescription: String = stringResource(Res.string.security_icon_description),
    externalOnClick: (() -> Unit)? = null,
) {
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    val fullContentDescription = baseContentDescription + " " + stringResource(securityState.descriptionResId)

    IconButton(
        onClick = {
            showHelpDialog = true
            externalOnClick?.invoke()
        },
    ) {
        SecurityIconDisplay(
            icon = securityState.icon,
            mainIconTint = securityState.color.invoke(),
            contentDescription = fullContentDescription,
            badgeIcon = securityState.badgeIcon,
            badgeIconColor = securityState.badgeIconColor.invoke(),
        )
    }

    if (showHelpDialog) {
        SecurityHelpDialog(securityState = securityState, onDismiss = { showHelpDialog = false })
    }
}

/**
 * Overload for [SecurityIcon] that derives the [SecurityState] from boolean flags.
 *
 * @param isLowEntropyKey Whether the channel uses a low entropy key.
 * @param isPreciseLocation Whether the channel has precise location enabled. Defaults to false.
 * @param isMqttEnabled Whether MQTT is enabled for the channel. Defaults to false.
 * @param baseContentDescription The base content description for the icon.
 * @param externalOnClick Optional lambda to be invoked when the icon is clicked, in addition to its primary action
 *   (showing a help dialog). This allows callers to inject custom side effects.
 */
@Composable
fun SecurityIcon(
    isLowEntropyKey: Boolean,
    isPreciseLocation: Boolean = false,
    isMqttEnabled: Boolean = false,
    baseContentDescription: String = stringResource(Res.string.security_icon_description),
    externalOnClick: (() -> Unit)? = null,
) {
    val securityState = determineSecurityState(isLowEntropyKey, isPreciseLocation, isMqttEnabled)
    SecurityIcon(
        securityState = securityState,
        baseContentDescription = baseContentDescription,
        externalOnClick = externalOnClick,
    )
}

/** Extension property to check if the channel uses a low entropy PSK (not securely encrypted). */
val Channel.isLowEntropyKey: Boolean
    get() = settings.psk.size <= 1

/** Extension property to check if the channel has precise location enabled. */
val Channel.isPreciseLocation: Boolean
    get() = settings.module_settings?.position_precision == PRECISE_POSITION_BITS

/** Extension property to check if MQTT is enabled for the channel. */
val Channel.isMqttEnabled: Boolean
    get() = settings.uplink_enabled ?: false

/**
 * Overload for [SecurityIcon] that takes a [Channel] object to determine its security state.
 *
 * @param channel The channel whose security status is to be displayed.
 * @param baseContentDescription The base content description for the icon.
 * @param externalOnClick Optional lambda for external actions, invoked when the icon is clicked.
 */
@Composable
fun SecurityIcon(
    channel: Channel,
    baseContentDescription: String = stringResource(Res.string.security_icon_description),
    externalOnClick: (() -> Unit)? = null,
) = SecurityIcon(
    isLowEntropyKey = channel.isLowEntropyKey,
    isPreciseLocation = channel.isPreciseLocation,
    isMqttEnabled = channel.isMqttEnabled,
    baseContentDescription = baseContentDescription,
    externalOnClick = externalOnClick,
)

/**
 * Overload for [SecurityIcon] that enables recomposition when making changes to the [ChannelSettings].
 *
 * @param baseContentDescription The base content description for the icon.
 * @param externalOnClick Optional lambda for external actions, invoked when the icon is clicked.
 */
@Composable
fun SecurityIcon(
    channelSettings: ChannelSettings,
    loraConfig: LoRaConfig,
    baseContentDescription: String = stringResource(Res.string.security_icon_description),
    externalOnClick: (() -> Unit)? = null,
) {
    val channel = Channel(channelSettings, loraConfig)
    SecurityIcon(
        isLowEntropyKey = channel.isLowEntropyKey,
        isPreciseLocation = channel.isPreciseLocation,
        isMqttEnabled = channel.isMqttEnabled,
        baseContentDescription = baseContentDescription,
        externalOnClick = externalOnClick,
    )
}

/**
 * Overload for [SecurityIcon] that takes an [AppOnlyProtos.ChannelSet] and a channel index. If the channel at the given
 * index is not found, nothing is rendered.
 *
 * @param channelSet The set of channels.
 * @param channelIndex The index of the channel within the set.
 * @param baseContentDescription The base content description for the icon.
 * @param externalOnClick Optional lambda for external actions, invoked when the icon is clicked.
 */
@Composable
fun SecurityIcon(
    channelSet: ChannelSet,
    channelIndex: Int,
    baseContentDescription: String = stringResource(Res.string.security_icon_description),
    externalOnClick: (() -> Unit)? = null,
) {
    channelSet.getChannel(channelIndex)?.let { channel ->
        SecurityIcon(
            channel = channel,
            baseContentDescription = baseContentDescription,
            externalOnClick = externalOnClick,
        )
    }
}

/**
 * Overload for [SecurityIcon] that takes an [AppOnlyProtos.ChannelSet] and a channel name. If a channel with the given
 * name is not found, nothing is rendered. This overload optimizes lookup by name by memoizing a map of channel names to
 * settings.
 *
 * @param channelSet The set of channels.
 * @param channelName The name of the channel to find.
 * @param baseContentDescription The base content description for the icon.
 * @param externalOnClick Optional lambda for external actions, invoked when the icon is clicked.
 */
@Composable
fun SecurityIcon(
    channelSet: ChannelSet,
    channelName: String,
    baseContentDescription: String = stringResource(Res.string.security_icon_description),
    externalOnClick: (() -> Unit)? = null,
) {
    val channelByNameMap =
        remember(channelSet) {
            channelSet.settings.associateBy { Channel(it, channelSet.lora_config ?: Channel.default.loraConfig).name }
        }

    channelByNameMap[channelName]?.let { channelSetting ->
        SecurityIcon(
            channel = Channel(channelSetting, channelSet.lora_config ?: Channel.default.loraConfig),
            baseContentDescription = baseContentDescription,
            externalOnClick = externalOnClick,
        )
    }
}

/**
 * Displays a help dialog explaining the meaning of different security icons. The dialog can show details for a specific
 * [SecurityState] or a list of all states.
 *
 * @param securityState The initial security state to display contextually.
 * @param onDismiss Lambda invoked when the dialog is dismissed.
 */
@Composable
private fun SecurityHelpDialog(securityState: SecurityState, onDismiss: () -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        modifier =
        if (showAll) {
            Modifier.fillMaxSize()
        } else {
            Modifier
        },
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (showAll) {
                    stringResource(Res.string.security_icon_help_title_all)
                } else {
                    stringResource(Res.string.security_icon_help_title)
                },
            )
        },
        text = {
            if (showAll) {
                AllSecurityStates()
            } else {
                ContextualSecurityState(securityState)
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
 * Displays details for a single, specific security state within the help dialog.
 *
 * @param securityState The state to display.
 */
@Composable
private fun ContextualSecurityState(securityState: SecurityState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SecurityIconDisplay(
            icon = securityState.icon,
            mainIconTint = securityState.color.invoke(),
            contentDescription = stringResource(securityState.descriptionResId),
            modifier = Modifier.size(48.dp),
            badgeIcon = securityState.badgeIcon,
            badgeIconColor = securityState.badgeIconColor.invoke(),
        )
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(securityState.helpTextResId), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Displays a list of all possible security states with their icons and descriptions within the help dialog. Iterates
 * over `SecurityState.entries` which is provided by the enum class.
 */
@Composable
private fun AllSecurityStates() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        SecurityState.entries.forEach { state ->
            // Uses enum entries
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecurityIconDisplay(
                    icon = state.icon,
                    mainIconTint = state.color.invoke(),
                    contentDescription = stringResource(state.descriptionResId),
                    modifier = Modifier.size(48.dp),
                    badgeIcon = state.badgeIcon,
                    badgeIconColor = state.badgeIconColor.invoke(),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = stringResource(state.descriptionResId), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(state.helpTextResId), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state != SecurityState.entries.lastOrNull()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// Preview functions for development and testing

@Preview(name = "Secure Channel Icon")
@Composable
private fun PreviewSecureChannel() {
    SecurityIcon(securityState = SecurityState.SECURE)
}

@Preview(name = "Insecure Precise Icon")
@Composable
private fun PreviewInsecureChannelWithPreciseLocation() {
    SecurityIcon(securityState = SecurityState.INSECURE_PRECISE_ONLY)
}

@Preview(name = "Insecure Channel Icon")
@Composable
private fun PreviewInsecureChannelWithoutPreciseLocation() {
    SecurityIcon(securityState = SecurityState.INSECURE_NO_PRECISE)
}

@Preview(name = "MQTT Enabled Icon")
@Composable
private fun PreviewMqttEnabled() {
    SecurityIcon(securityState = SecurityState.INSECURE_PRECISE_MQTT_WARNING)
}

@Preview(name = "All Security Icons with Dialog")
@Composable
private fun PreviewAllSecurityIconsWithDialog() {
    var showHelpDialogFor by remember { mutableStateOf<SecurityState?>(null) }
    val stateLabels = remember {
        // Using SecurityState.entries to build the map keys
        mapOf(
            SecurityState.SECURE to "Secure",
            SecurityState.INSECURE_NO_PRECISE to "Insecure (No Precise Location)",
            SecurityState.INSECURE_PRECISE_ONLY to "Insecure (Precise Location Only)",
            SecurityState.INSECURE_PRECISE_MQTT_WARNING to "Insecure (Precise Location + MQTT Warning)",
        )
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Security Icons Preview (Click for Help)", style = MaterialTheme.typography.headlineSmall)

        SecurityState.entries.forEach { state ->
            // Iterate over enum entries
            val label = stateLabels[state] ?: "Unknown State (${state.name})" // Fallback to enum name
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SecurityIcon(securityState = state, externalOnClick = { showHelpDialogFor = state })
                Text(label)
            }
        }
        showHelpDialogFor?.let { SecurityHelpDialog(securityState = it, onDismiss = { showHelpDialogFor = null }) }
    }
}
