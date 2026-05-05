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

/**
 * Persists app-local node metadata that survives process death.
 * These fields are user preferences/annotations that the SDK does not manage.
 */
@Entity(tableName = "node_metadata")
data class NodeMetadataEntity(
    @PrimaryKey val num: Int,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_ignored", defaultValue = "0") val isIgnored: Boolean = false,
    @ColumnInfo(name = "is_muted", defaultValue = "0") val isMuted: Boolean = false,
    @ColumnInfo(name = "notes", defaultValue = "") val notes: String = "",
    @ColumnInfo(name = "manually_verified", defaultValue = "0") val manuallyVerified: Boolean = false,
)
