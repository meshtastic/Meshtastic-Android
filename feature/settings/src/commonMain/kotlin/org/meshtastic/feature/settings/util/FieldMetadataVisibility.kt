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

import org.meshtastic.proto.FieldMetadata

/**
 * Whether a control is offered on this board. A `diy_only` field is hidden only on a board the registry knows and does
 * not tag DIY ([isDiyHardware] false); an unknown board shows it, and so does a field already holding a value, which
 * would otherwise be saved back unseen.
 */
fun FieldMetadata.offeredOn(isDiyHardware: Boolean?, holdsValue: Boolean): Boolean =
    diy_only != true || isDiyHardware != false || holdsValue
