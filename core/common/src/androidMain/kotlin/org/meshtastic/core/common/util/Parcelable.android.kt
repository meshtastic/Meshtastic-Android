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
package org.meshtastic.core.common.util

import android.os.Parcelable

actual typealias CommonParcelable = Parcelable

actual typealias CommonParcelize = kotlinx.parcelize.Parcelize

actual typealias CommonIgnoredOnParcel = kotlinx.parcelize.IgnoredOnParcel

actual typealias CommonParceler<T> = kotlinx.parcelize.Parceler<T>

actual typealias CommonTypeParceler<T, P> = kotlinx.parcelize.TypeParceler<T, P>

actual typealias CommonParcel = android.os.Parcel
