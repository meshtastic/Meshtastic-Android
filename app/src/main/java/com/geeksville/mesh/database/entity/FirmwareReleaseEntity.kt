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

package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.network.model.NetworkFirmwareRelease
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "firmware_release")
data class FirmwareReleaseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "",
    @ColumnInfo(name = "page_url")
    val pageUrl: String = "",
    @ColumnInfo(name = "release_notes")
    val releaseNotes: String = "",
    @ColumnInfo(name = "title")
    val title: String = "",
    @ColumnInfo(name = "zip_url")
    val zipUrl: String = "",
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "release_type")
    val releaseType: FirmwareReleaseType = FirmwareReleaseType.STABLE,
)

fun NetworkFirmwareRelease.asEntity(releaseType: FirmwareReleaseType) = FirmwareReleaseEntity(
    id = id,
    pageUrl = pageUrl,
    releaseNotes = releaseNotes,
    title = title,
    zipUrl = zipUrl,
    lastUpdated = System.currentTimeMillis(),
    releaseType = releaseType,
)

fun FirmwareReleaseEntity.asExternalModel() = FirmwareRelease(
    id = id,
    pageUrl = pageUrl,
    releaseNotes = releaseNotes,
    title = title,
    zipUrl = zipUrl,
    lastUpdated = lastUpdated,
    releaseType = releaseType,
)

data class FirmwareRelease(
    val id: String = "",
    val pageUrl: String = "",
    val releaseNotes: String = "",
    val title: String = "",
    val zipUrl: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    val releaseType: FirmwareReleaseType = FirmwareReleaseType.STABLE,
)

enum class FirmwareReleaseType {
    STABLE,
    ALPHA
}
