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
package org.meshtastic.core.ui.label

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanStartFailureReason
import org.meshtastic.core.ble.niimbot.NiimbotFault
import org.meshtastic.core.resources.NotoEmoji_Regular
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth_scan_location_services_disabled
import org.meshtastic.core.resources.bluetooth_scan_start_failed
import org.meshtastic.core.resources.grant_permission
import org.meshtastic.core.resources.label_print_channel_title
import org.meshtastic.core.resources.label_print_connect_title
import org.meshtastic.core.resources.label_print_connecting
import org.meshtastic.core.resources.label_print_error_bluetooth
import org.meshtastic.core.resources.label_print_error_busy
import org.meshtastic.core.resources.label_print_error_canceled
import org.meshtastic.core.resources.label_print_error_cover_open
import org.meshtastic.core.resources.label_print_error_head_hot
import org.meshtastic.core.resources.label_print_error_labels_out
import org.meshtastic.core.resources.label_print_error_low_battery
import org.meshtastic.core.resources.label_print_error_no_labels
import org.meshtastic.core.resources.label_print_error_no_response
import org.meshtastic.core.resources.label_print_error_other
import org.meshtastic.core.resources.label_print_error_rejected
import org.meshtastic.core.resources.label_print_error_scan_busy
import org.meshtastic.core.resources.label_print_error_unsupported
import org.meshtastic.core.resources.label_print_error_wrong_roll
import org.meshtastic.core.resources.label_print_include_pin
import org.meshtastic.core.resources.label_print_length
import org.meshtastic.core.resources.label_print_length_mm
import org.meshtastic.core.resources.label_print_name_tag_title
import org.meshtastic.core.resources.label_print_none_found
import org.meshtastic.core.resources.label_print_permission
import org.meshtastic.core.resources.label_print_pin_warning
import org.meshtastic.core.resources.label_print_preview_channel
import org.meshtastic.core.resources.label_print_preview_connect
import org.meshtastic.core.resources.label_print_preview_name_tag
import org.meshtastic.core.resources.label_print_print
import org.meshtastic.core.resources.label_print_print_another
import org.meshtastic.core.resources.label_print_printer
import org.meshtastic.core.resources.label_print_printing
import org.meshtastic.core.resources.label_print_qr_too_dense_channels
import org.meshtastic.core.resources.label_print_qr_too_dense_connect
import org.meshtastic.core.resources.label_print_qr_too_dense_contact
import org.meshtastic.core.resources.label_print_search_again
import org.meshtastic.core.resources.label_print_searching
import org.meshtastic.core.resources.label_print_stock_hint
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Print
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.PermissionUiState
import org.meshtastic.core.ui.util.isBluetoothDisabled
import org.meshtastic.core.ui.util.rememberBluetoothPermissionState
import org.meshtastic.core.ui.util.rememberOpenBluetoothSettings

/** Die-cut lengths offered for 12 mm stock, in millimeters. */
private val LABEL_LENGTHS_MM = listOf(30, 40)

/**
 * Prints [spec] on a Niimbot D110: a preview, the label length, the printer, and Print. The length and the printer are
 * remembered, and the printer used last reconnects when it is seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPrintSheet(spec: LabelSpec, onDismiss: () -> Unit, viewModel: LabelPrintViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lengthMm by viewModel.labelLengthMm.collectAsStateWithLifecycle()
    // A connect label can leave the pairing PIN out of its QR; the choice is made here, per print.
    var includePin by rememberSaveable { mutableStateOf(true) }
    val shown = if (spec is LabelSpec.Connect) spec.withPin(includePin) else spec
    val rendered = rememberRenderedLabel(shown, lengthMm)
    val permission = rememberBluetoothPermissionState()
    val bluetoothOff = isBluetoothDisabled()

    // The search runs while the sheet is up and Bluetooth can be used, and the connection ends with the sheet.
    DisposableEffect(viewModel, permission.isGranted, bluetoothOff) {
        if (permission.isGranted && !bluetoothOff) viewModel.start()
        onDispose { viewModel.stop() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(spec.titleRes),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            LabelPreview(shown, rendered)
            if (spec is LabelSpec.Connect && spec.linkWithoutPin != null) {
                PinOption(included = includePin, onChange = { includePin = it })
            }
            LabelLengthPicker(lengthMm, viewModel::setLabelLengthMm)
            SectionTitle(Res.string.label_print_printer)
            PrinterAccess(permission, bluetoothOff) {
                PrinterSection(state, onConnect = viewModel::connect, onSearch = viewModel::start)
            }
            state.error?.let { Text(text = it.message(), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { rendered?.let { viewModel.print(it.bitmap) } },
                enabled = state.status == LabelPrinterStatus.READY && rendered != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = MeshtasticIcons.Print, contentDescription = null)
                Text(text = stringResource(state.printLabel), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val LabelSpec.titleRes: StringResource
    get() =
        when (this) {
            is LabelSpec.NameTag -> Res.string.label_print_name_tag_title
            is LabelSpec.Channels -> Res.string.label_print_channel_title
            is LabelSpec.Connect -> Res.string.label_print_connect_title
        }

/** Says what a PIN in the QR means, and lets the label be printed without it. */
@Composable
private fun PinOption(included: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier =
            Modifier.fillMaxWidth()
                .toggleable(value = included, role = Role.Switch, onValueChange = onChange)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(Res.string.label_print_include_pin), modifier = Modifier.weight(1f))
            Switch(checked = included, onCheckedChange = null)
        }
        if (included) {
            Text(
                text = stringResource(Res.string.label_print_pin_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** What stands between the sheet and a search, with the way to fix it, or [printers] when nothing does. */
@Composable
private fun PrinterAccess(permission: PermissionUiState, bluetoothOff: Boolean, printers: @Composable () -> Unit) {
    val blocked = permission.status == PermissionStatus.PERMANENTLY_DENIED
    when {
        !permission.isGranted ->
            Recovery(
                message = Res.string.label_print_permission,
                action = if (blocked) Res.string.open_settings else Res.string.grant_permission,
                onAction = if (blocked) permission.openAppSettings else permission.request,
            )

        bluetoothOff ->
            Recovery(
                message = Res.string.label_print_error_bluetooth,
                action = Res.string.open_settings,
                onAction = rememberOpenBluetoothSettings(),
            )

        else -> printers()
    }
}

private val LabelPrintState.printLabel: StringResource
    get() =
        when {
            status == LabelPrinterStatus.PRINTING -> Res.string.label_print_printing
            printed -> Res.string.label_print_print_another
            else -> Res.string.label_print_print
        }

/** The label for [lengthMm], or null when its QR is too dense to print. */
@Composable
private fun rememberRenderedLabel(spec: LabelSpec, lengthMm: Int): RenderedLabel? {
    val textMeasurer = rememberTextMeasurer()
    val emojiFont = FontFamily(Font(Res.font.NotoEmoji_Regular))
    val rasterizer = remember(textMeasurer, emojiFont) { LabelRasterizer(textMeasurer, emojiFont) }
    return remember(spec, lengthMm, rasterizer) {
        spec.layout(labelLengthDots(lengthMm), rasterizer)?.let(rasterizer::render)
    }
}

@Composable
private fun LabelPreview(spec: LabelSpec, rendered: RenderedLabel?) {
    if (rendered == null) {
        val reason =
            when (spec) {
                is LabelSpec.NameTag -> Res.string.label_print_qr_too_dense_contact
                is LabelSpec.Channels -> Res.string.label_print_qr_too_dense_channels
                is LabelSpec.Connect -> Res.string.label_print_qr_too_dense_connect
            }
        Text(text = stringResource(reason), color = MaterialTheme.colorScheme.error)
    } else {
        val description =
            when (spec) {
                is LabelSpec.NameTag -> stringResource(Res.string.label_print_preview_name_tag, spec.content.longName)
                is LabelSpec.Channels -> stringResource(Res.string.label_print_preview_channel, spec.content.title)
                is LabelSpec.Connect -> stringResource(Res.string.label_print_preview_connect, spec.content.longName)
            }
        Image(
            bitmap = rendered.preview,
            contentDescription = description,
            modifier =
            Modifier.fillMaxWidth()
                .background(Color.White, MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(4.dp),
            contentScale = ContentScale.FillWidth,
            filterQuality = FilterQuality.None,
        )
    }
}

@Composable
private fun LabelLengthPicker(lengthMm: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(Res.string.label_print_length)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LABEL_LENGTHS_MM.forEachIndexed { index, length ->
                SegmentedButton(
                    label = { Text(text = stringResource(Res.string.label_print_length_mm, length)) },
                    onClick = { onSelect(length) },
                    selected = lengthMm == length,
                    shape = SegmentedButtonDefaults.itemShape(index, LABEL_LENGTHS_MM.size),
                )
            }
        }
        Text(
            text = stringResource(Res.string.label_print_stock_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(title: StringResource) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun PrinterSection(state: LabelPrintState, onConnect: (BleDevice) -> Unit, onSearch: () -> Unit) {
    val name = state.printerName ?: stringResource(Res.string.label_print_printer)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.status) {
            LabelPrinterStatus.READY,
            LabelPrinterStatus.PRINTING,
            -> Text(text = name)

            LabelPrinterStatus.CONNECTING -> Text(text = stringResource(Res.string.label_print_connecting, name))

            LabelPrinterStatus.SEARCHING,
            LabelPrinterStatus.IDLE,
            -> {
                val searching = state.status == LabelPrinterStatus.SEARCHING
                if (state.found.isEmpty()) {
                    val hint = if (searching) Res.string.label_print_searching else Res.string.label_print_none_found
                    Text(text = stringResource(hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.found.forEach { device ->
                    OutlinedButton(onClick = { onConnect(device) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = device.name ?: stringResource(Res.string.label_print_printer))
                    }
                }
                if (!searching) {
                    OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(Res.string.label_print_search_again))
                    }
                }
            }
        }
    }
}

@Composable
private fun Recovery(message: StringResource, action: StringResource, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(text = stringResource(action)) }
    }
}

@Composable
private fun LabelPrintError.message(): String = when (this) {
    is LabelPrintError.Fault ->
        when (fault) {
            NiimbotFault.COVER_OPEN -> stringResource(Res.string.label_print_error_cover_open)
            NiimbotFault.NO_LABELS -> stringResource(Res.string.label_print_error_no_labels)
            NiimbotFault.LOW_BATTERY -> stringResource(Res.string.label_print_error_low_battery)
            NiimbotFault.CANCELED -> stringResource(Res.string.label_print_error_canceled)
            NiimbotFault.HEAD_TOO_HOT -> stringResource(Res.string.label_print_error_head_hot)
            NiimbotFault.LABELS_OUT_OR_LID_UNLATCHED -> stringResource(Res.string.label_print_error_labels_out)
            NiimbotFault.BUSY -> stringResource(Res.string.label_print_error_busy)
            NiimbotFault.WRONG_ROLL -> stringResource(Res.string.label_print_error_wrong_roll)
            NiimbotFault.OTHER -> stringResource(Res.string.label_print_error_other, code.toString(HEX))
        }

    LabelPrintError.Unsupported -> stringResource(Res.string.label_print_error_unsupported)

    LabelPrintError.NoResponse -> stringResource(Res.string.label_print_error_no_response)

    LabelPrintError.Rejected -> stringResource(Res.string.label_print_error_rejected)

    is LabelPrintError.Scan ->
        when (reason) {
            BleScanStartFailureReason.ScanningTooFrequently ->
                stringResource(Res.string.label_print_error_scan_busy)

            BleScanStartFailureReason.BluetoothDisabled -> stringResource(Res.string.label_print_error_bluetooth)

            // Android 11 and older only scan with location services on.
            BleScanStartFailureReason.LocationServicesDisabled ->
                stringResource(Res.string.bluetooth_scan_location_services_disabled)

            BleScanStartFailureReason.MissingScanPermission -> stringResource(Res.string.label_print_permission)

            BleScanStartFailureReason.ApplicationRegistrationFailed,
            null,
            -> stringResource(Res.string.bluetooth_scan_start_failed)
        }
}

private const val HEX = 16
