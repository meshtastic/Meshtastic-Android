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
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.meshtastic.feature.map.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.latitude
import org.meshtastic.core.resources.longitude
import org.meshtastic.core.resources.name
import org.meshtastic.core.resources.site_planner
import org.meshtastic.core.resources.site_planner_antenna_gain_dbi
import org.meshtastic.core.resources.site_planner_antenna_height_meters
import org.meshtastic.core.resources.site_planner_color_scale
import org.meshtastic.core.resources.site_planner_estimate
import org.meshtastic.core.resources.site_planner_frequency_mhz
import org.meshtastic.core.resources.site_planner_invalid_latitude
import org.meshtastic.core.resources.site_planner_invalid_longitude
import org.meshtastic.core.resources.site_planner_invalid_positive
import org.meshtastic.core.resources.site_planner_subtitle
import org.meshtastic.core.resources.site_planner_tx_power_watts
import org.meshtastic.core.resources.site_planner_use_current_location
import org.meshtastic.core.resources.site_planner_use_map_center
import org.meshtastic.core.resources.site_planner_use_node_location
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MyLocation
import org.meshtastic.core.ui.icon.PinDrop

private const val LAT_LIMIT = 90.0
private const val LON_LIMIT = 180.0
private val SWATCH_WIDTH = 40.dp
private val SWATCH_HEIGHT = 16.dp

/** Editable form state; string-backed so partial/invalid input is preserved while typing. */
@Stable
private class SiteFormState(initial: SitePlannerParams) {
    var name by mutableStateOf(initial.name)
    var lat by mutableStateOf(initial.latitude.toString())
    var lon by mutableStateOf(initial.longitude.toString())
    var power by mutableStateOf(initial.txPowerWatts.toString())
    var freq by mutableStateOf(initial.txFreqMhz.toString())
    var height by mutableStateOf(initial.txHeightMeters.toString())
    var gain by mutableStateOf(initial.txGainDbi.toString())
    var colorScale by mutableStateOf(initial.colorScale)
}

/**
 * Bottom-sheet form for a Site Planner coverage estimate, pre-filled with [initial]. [onSubmit] receives the edited
 * params once they validate. Location shortcut chips ([onUseCurrentLocation]/[onUseNodeLocation]/[onUseMapCenter])
 * re-seed the coordinate fields when provided, preserving edits to the other fields.
 */
@Composable
fun SitePlannerSheet(
    initial: SitePlannerParams,
    onSubmit: (SitePlannerParams) -> Unit,
    onDismiss: () -> Unit,
    onUseCurrentLocation: (() -> Unit)? = null,
    onUseNodeLocation: (() -> Unit)? = null,
    onUseMapCenter: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = remember { SiteFormState(initial) }
    // Re-seed only the coordinates when a shortcut changes them; other fields keep the user's edits.
    LaunchedEffect(initial.latitude, initial.longitude) {
        state.lat = initial.latitude.toString()
        state.lon = initial.longitude.toString()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SiteFormContent(state, initial, onSubmit, onUseCurrentLocation, onUseNodeLocation, onUseMapCenter)
    }
}

@Composable
private fun SiteFormContent(
    state: SiteFormState,
    initial: SitePlannerParams,
    onSubmit: (SitePlannerParams) -> Unit,
    onUseCurrentLocation: (() -> Unit)?,
    onUseNodeLocation: (() -> Unit)?,
    onUseMapCenter: (() -> Unit)?,
) {
    val latDeg = state.lat.toDoubleOrNull()
    val lonDeg = state.lon.toDoubleOrNull()
    val latBad = latDeg == null || latDeg !in -LAT_LIMIT..LAT_LIMIT
    val lonBad = lonDeg == null || lonDeg !in -LON_LIMIT..LON_LIMIT
    val powerBad = (state.power.toDoubleOrNull() ?: 0.0) <= 0.0
    val freqBad = (state.freq.toDoubleOrNull() ?: 0.0) <= 0.0
    // Also guard the null-island (0,0) case so an empty ocean run can't be submitted.
    val canSubmit = !latBad && !lonBad && !powerBad && !freqBad && (latDeg != 0.0 || lonDeg != 0.0)
    val latMsg = stringResource(Res.string.site_planner_invalid_latitude)
    val lonMsg = stringResource(Res.string.site_planner_invalid_longitude)
    val posMsg = stringResource(Res.string.site_planner_invalid_positive)

    Column(
        modifier =
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.site_planner),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.site_planner_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SiteField(state.name, { state.name = it }, Res.string.name, keyboardType = KeyboardType.Text)
        LocationChips(onUseCurrentLocation, onUseNodeLocation, onUseMapCenter)
        TransmitterFields(
            state = state,
            latError = if (latBad) latMsg else null,
            lonError = if (lonBad) lonMsg else null,
            powerError = if (powerBad) posMsg else null,
            freqError = if (freqBad) posMsg else null,
        )
        PalettePicker(state.colorScale) { state.colorScale = it }
        Button(
            onClick = { onSubmit(buildSubmitParams(state, initial)) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.site_planner_estimate))
        }
    }
}

/** Quick shortcuts (chips) that re-seed the coordinates from the device, this node, or the map center. */
@Composable
private fun LocationChips(
    onUseCurrentLocation: (() -> Unit)?,
    onUseNodeLocation: (() -> Unit)?,
    onUseMapCenter: (() -> Unit)?,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        onUseCurrentLocation?.let {
            LocationChip(it, MeshtasticIcons.MyLocation, Res.string.site_planner_use_current_location)
        }
        onUseNodeLocation?.let { LocationChip(it, MeshtasticIcons.PinDrop, Res.string.site_planner_use_node_location) }
        onUseMapCenter?.let { LocationChip(it, MeshtasticIcons.Map, Res.string.site_planner_use_map_center) }
    }
}

@Composable
private fun LocationChip(onClick: () -> Unit, icon: ImageVector, label: StringResource) {
    AssistChip(
        onClick = onClick,
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun TransmitterFields(
    state: SiteFormState,
    latError: String?,
    lonError: String?,
    powerError: String?,
    freqError: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SiteField(state.lat, { state.lat = it }, Res.string.latitude, error = latError)
        SiteField(state.lon, { state.lon = it }, Res.string.longitude, error = lonError)
        SiteField(state.power, { state.power = it }, Res.string.site_planner_tx_power_watts, error = powerError)
        SiteField(state.freq, { state.freq = it }, Res.string.site_planner_frequency_mhz, error = freqError)
        SiteField(state.height, { state.height = it }, Res.string.site_planner_antenna_height_meters)
        SiteField(state.gain, { state.gain = it }, Res.string.site_planner_antenna_gain_dbi)
    }
}

@Composable
private fun SiteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Color-scale picker with a gradient swatch for each palette (recognition over recall). */
@Composable
private fun PalettePicker(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = SitePlannerParams.COLOR_SCALES.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.site_planner_color_scale)) },
            leadingIcon = { PaletteSwatch(value) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SitePlannerParams.COLOR_SCALES.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { PaletteSwatch(key) },
                    onClick = {
                        onChange(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(name: String) {
    Box(
        Modifier.width(SWATCH_WIDTH)
            .height(SWATCH_HEIGHT)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(paletteStops(name))),
    )
}

/** Representative gradient stops per planner palette (approximations of the matplotlib LUTs for a preview swatch). */
@Suppress("MagicNumber")
private fun paletteStops(name: String): List<Color> {
    val argb =
        when (name) {
            "viridis" -> listOf(0xFF440154, 0xFF31688E, 0xFF35B779, 0xFFFDE725)
            "CMRmap" -> listOf(0xFF000000, 0xFF3B0F70, 0xFF8C2981, 0xFFDE4968, 0xFFFE9F6D, 0xFFFFFFFF)
            "cool" -> listOf(0xFF00FFFF, 0xFFFF00FF)
            "turbo" -> listOf(0xFF30123B, 0xFF4585F9, 0xFF1BD0D5, 0xFFA4FC3B, 0xFFFABA39, 0xFFE5460A, 0xFF7A0403)
            "jet" -> listOf(0xFF000080, 0xFF0000FF, 0xFF00FFFF, 0xFFFFFF00, 0xFFFF0000, 0xFF800000)
            else -> listOf(0xFF0D0887, 0xFF7E03A8, 0xFFCC4778, 0xFFF89540, 0xFFF0F921)
        }
    return argb.map { Color(it) }
}

/** Build params from the (string) form fields, falling back to the matching [initial] value when a field is invalid. */
private fun buildSubmitParams(state: SiteFormState, initial: SitePlannerParams): SitePlannerParams = SitePlannerParams(
    name = state.name.ifBlank { initial.name },
    latitude = state.lat.toDoubleOrNull() ?: initial.latitude,
    longitude = state.lon.toDoubleOrNull() ?: initial.longitude,
    txPowerWatts = state.power.toDoubleOrNull() ?: initial.txPowerWatts,
    txFreqMhz = state.freq.toDoubleOrNull() ?: initial.txFreqMhz,
    txHeightMeters = state.height.toDoubleOrNull() ?: initial.txHeightMeters,
    txGainDbi = state.gain.toDoubleOrNull() ?: initial.txGainDbi,
    colorScale = state.colorScale,
)
