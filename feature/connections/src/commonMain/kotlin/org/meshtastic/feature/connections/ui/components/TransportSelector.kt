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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.network
import org.meshtastic.core.resources.usb
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Usb
import org.meshtastic.core.ui.icon.Wifi

/**
 * Single-choice transport selector rendered below the connection card. A Material 3 [SingleChoiceSegmentedButtonRow]
 * makes the mutually-exclusive choice explicit: the segments read as one grouped control and the selected transport
 * shows a check, rather than three independent chips whose filled state was read as "enabled/available" instead of
 * "selected".
 *
 * @param showBluetooth false on hardware with no Bluetooth LE, where the BLE segment could never find anything.
 * @param showUsb false on hardware with no USB host, unless Demo Mode needs the USB pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportSelector(
    activeTransport: DeviceType,
    onSelectTransport: (DeviceType) -> Unit,
    modifier: Modifier = Modifier,
    showBluetooth: Boolean = true,
    showUsb: Boolean = true,
) {
    val transports =
        DeviceType.entries.filter { (it != DeviceType.BLE || showBluetooth) && (it != DeviceType.USB || showUsb) }
    val labels = transports.map { stringResource(it.label) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    // Fill the width so the control reads as one deliberate group spanning the same width as the connection card
    // above; each SegmentedButton carries an internal weight(1f), so the segments divide the row evenly.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // SegmentedButton never constrains its label: a long one spills into the padding and then under the border,
        // so whether it fits has to be measured here rather than read back from the text layout.
        val segmentWidth = maxWidth / transports.size
        val labelsFit =
            remember(labels, labelStyle, density, segmentWidth) {
                labels.all { label ->
                    val textWidth = textMeasurer.measure(label, labelStyle, softWrap = false, density = density)
                    with(density) { textWidth.size.width.toDp() } + LabeledSegmentMinimumChrome <= segmentWidth
                }
            }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            transports.forEachIndexed { index, transport ->
                TransportSegment(
                    selected = activeTransport == transport,
                    index = index,
                    count = transports.size,
                    label = labels[index],
                    icon = transport.icon,
                    showLabel = labelsFit,
                    onClick = { onSelectTransport(transport) },
                )
            }
        }
    }
}

/**
 * A labeled segment's icon and icon gap, plus a 4dp inset each side so the label never meets the divider. Three labels
 * at default font on a 360dp phone keep about 9dp of headroom, so font metrics that differ by a few percent between
 * devices don't flip the selector to icons.
 */
private val LabeledSegmentMinimumChrome = SegmentedButtonDefaults.IconSize + 8.dp + 4.dp * 2

private val DeviceType.label: StringResource
    get() =
        when (this) {
            DeviceType.BLE -> Res.string.bluetooth
            DeviceType.TCP -> Res.string.network
            DeviceType.USB -> Res.string.usb
        }

private val DeviceType.icon: ImageVector
    @Composable
    get() =
        when (this) {
            DeviceType.BLE -> MeshtasticIcons.Bluetooth
            DeviceType.TCP -> MeshtasticIcons.Wifi
            DeviceType.USB -> MeshtasticIcons.Usb
        }

/** Test tag prefix for a labeled segment's transport icon, suffixed with `:<label>`. */
internal const val TRANSPORT_ICON_TAG = "TransportIcon"

/**
 * A single transport segment. Labeled, it shows a check when [selected] and the transport [icon] otherwise, so
 * selection is unambiguous while the unselected segments still communicate which transport they represent. When
 * [showLabel] is false (some label would not fit at this width and font scale) the segment is icon-only: the transport
 * icon carries [label] as its content description and the check marks the selected one. The section header below names
 * the active pane, so nothing is lost visually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceSegmentedButtonRowScope.TransportSegment(
    selected: Boolean,
    index: Int,
    count: Int,
    label: String,
    icon: ImageVector,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = count),
        icon = {
            // SegmentedButton measures this slot at the label's full height, which pins a bare 18dp icon to the top
            // of the slot once the label is taller. Each icon centers itself in that height instead.
            val activeIcon: @Composable () -> Unit = { CenteredSegmentIcon { SegmentedButtonDefaults.ActiveIcon() } }
            if (showLabel) {
                SegmentedButtonDefaults.Icon(active = selected, activeContent = activeIcon) {
                    CenteredSegmentIcon {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier =
                            Modifier.size(SegmentedButtonDefaults.IconSize).testTag("$TRANSPORT_ICON_TAG:$label"),
                        )
                    }
                }
            } else {
                SegmentedButtonDefaults.Icon(active = selected, activeContent = activeIcon)
            }
        },
        label = {
            if (showLabel) {
                Text(text = label, maxLines = 1)
            } else {
                Icon(imageVector = icon, contentDescription = label)
            }
        },
    )
}

/** Fills the height SegmentedButton gives its icon slot and centers [icon] in it. */
@Composable
private fun CenteredSegmentIcon(icon: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { icon() }
}
