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
package org.meshtastic.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import org.meshtastic.core.model.DeviceLink

/** A msh.to device link, cached from the Meshtastic API (`/resource/deviceLinks`) during the refresh cycle. */
@Serializable
@Entity(tableName = "device_link")
data class DeviceLinkEntity(
    @PrimaryKey @ColumnInfo(name = "short_code") val shortCode: String,
    @ColumnInfo(name = "link_description") val linkDescription: String? = null,
    @ColumnInfo(name = "is_vendor") val isVendor: Boolean = false,
    val regions: List<String>? = null,
    val targets: List<String>? = null,
)

fun DeviceLink.asEntity() = DeviceLinkEntity(
    shortCode = shortCode,
    linkDescription = description,
    isVendor = isVendor,
    regions = regions,
    targets = targets,
)

fun DeviceLinkEntity.asExternalModel() = DeviceLink(
    shortCode = shortCode,
    description = linkDescription,
    isVendor = isVendor,
    regions = regions,
    targets = targets,
)
