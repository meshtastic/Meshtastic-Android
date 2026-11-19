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
@file:Suppress("UnusedImport")

package org.meshtastic.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step

/**
 * True when [sql] returns at least one row.
 *
 * androidx.sqlite declares `prepare` and `step` as members on each platform but not in common, and they suspend only on
 * web, so analysis of a non-web platform resolves them to its blocking members: it reports the imports unused and
 * `suspend` redundant, while common code needs both.
 */
@Suppress("RedundantSuspendModifier")
internal suspend fun SQLiteConnection.returnsRows(sql: String): Boolean = prepare(sql).use { it.step() }
