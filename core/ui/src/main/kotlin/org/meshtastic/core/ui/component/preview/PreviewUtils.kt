/*
 * Copyright (c) 2025 Meshtastic LLC
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
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.TelemetryProtos
import org.meshtastic.core.database.model.Node

/** Simple [PreviewParameterProvider] that provides true and false values. */
class BooleanProvider : PreviewParameterProvider<Boolean> {
    override val values: Sequence<Boolean> = sequenceOf(false, true)
}

private val user = MeshProtos.User.newBuilder().setShortName("\uD83E\uDEE0").setLongName("John Doe").build()
val previewNode =
    Node(
        num = 13444,
        user = user,
        isIgnored = false,
        paxcounter = PaxcountProtos.Paxcount.newBuilder().setBle(10).setWifi(5).build(),
        environmentMetrics =
        TelemetryProtos.EnvironmentMetrics.newBuilder().setTemperature(25f).setRelativeHumidity(60f).build(),
    )
