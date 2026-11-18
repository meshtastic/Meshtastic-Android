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
package org.meshtastic.feature.node.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.feature.node.model.GlbBounds

/** No 3D renderer on web: reports an error so the dialog shows its fallback. */
@Composable
internal actual fun DeviceModelScene(
    glb: ByteArray,
    bounds: GlbBounds,
    onError: () -> Unit,
    modifier: Modifier,
    visible: Boolean,
    screen: GlbBounds?,
    screenFrame: MirrorFrame?,
) {
    val currentOnError by rememberUpdatedState(onError)
    LaunchedEffect(Unit) { currentOnError() }
}

internal actual val deviceModelDrawsScreen: Boolean = false
