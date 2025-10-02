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

package org.meshtastic.core.model.util

import android.util.Base64
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString

fun ByteString.encodeToString() = Base64.encodeToString(this.toByteArray(), Base64.NO_WRAP)

fun String.toByteString() = Base64.decode(this, Base64.NO_WRAP).toByteString()
