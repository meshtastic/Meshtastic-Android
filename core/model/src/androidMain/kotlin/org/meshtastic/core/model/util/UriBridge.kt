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
package org.meshtastic.core.model.util

import android.net.Uri
import com.eygraber.uri.toKmpUri
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.SharedContact

/** Extension to bridge android.net.Uri to CommonUri for shared dispatch logic. */
fun Uri.toCommonUri(): CommonUri = this.toKmpUri()

/** Bridge extension for Android clients. */
fun Uri.dispatchMeshtasticUri(
    onChannel: (ChannelSet) -> Unit,
    onContact: (SharedContact) -> Unit,
    onInvalid: () -> Unit,
) = this.toCommonUri().dispatchMeshtasticUri(onChannel, onContact, onInvalid)

/** Bridge extension for Android clients. */
fun Uri.toChannelSet(): ChannelSet = this.toCommonUri().toChannelSet()

/** Bridge extension for Android clients. */
fun Uri.toSharedContact(): SharedContact = this.toCommonUri().toSharedContact()
