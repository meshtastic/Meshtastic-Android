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
package org.meshtastic.core.database

import androidx.sqlite.SQLiteConnection

// androidx.sqlite declares these blocking off web and suspending on web, so commonMain reaches them through here.

/** Runs one statement that returns no rows. */
internal expect suspend fun SQLiteConnection.execStatement(sql: String)

/** True when [sql] returns at least one row. */
internal expect suspend fun SQLiteConnection.returnsRows(sql: String): Boolean
