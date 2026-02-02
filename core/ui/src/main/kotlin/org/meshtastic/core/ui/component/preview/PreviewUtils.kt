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
@file:Suppress("MatchingDeclarationName")

package org.meshtastic.core.ui.component.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.meshtastic.core.database.model.Node
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.User

/** Simple [PreviewParameterProvider] that provides true and false values. */
class BooleanProvider : PreviewParameterProvider<Boolean> {
    override val values: Sequence<Boolean> = sequenceOf(false, true)
}

private val user = User(short_name = "\uD83E\uDEE0", long_name = "John Doe")
val previewNode =
    Node(
        num = 13444,
        user = user,
        isIgnored = false,
        paxcounter = Paxcount(ble = 10, wifi = 5),
        environmentMetrics = EnvironmentMetrics(temperature = 25f, relative_humidity = 60f),
    )
