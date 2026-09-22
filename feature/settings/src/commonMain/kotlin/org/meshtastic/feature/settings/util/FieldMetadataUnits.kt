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
package org.meshtastic.feature.settings.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.label_with_unit
import org.meshtastic.core.resources.unit_dbm
import org.meshtastic.core.resources.unit_khz
import org.meshtastic.core.resources.unit_meters
import org.meshtastic.proto.FieldMetadata

/**
 * The translated name of the unit a proto field declares, or null where the app has no name for that symbol. The
 * schema's symbol is never rendered directly: a symbol with no entry here shows no unit rather than raw schema text.
 */
val FieldMetadata.unitLabelRes: StringResource?
    get() =
        when (unit) {
            "m" -> Res.string.unit_meters
            "dBm" -> Res.string.unit_dbm
            "kHz" -> Res.string.unit_khz
            else -> null
        }

/**
 * A control's title, with the schema's unit appended when the control shows a bare number. An interval picker names its
 * own unit in every item ("30 seconds", "2 hours"), so those pass no metadata and keep the label alone.
 */
@Composable
fun fieldTitle(label: StringResource, metadata: FieldMetadata): String {
    val unitRes = metadata.unitLabelRes ?: return stringResource(label)
    return stringResource(Res.string.label_with_unit, stringResource(label), stringResource(unitRes))
}
