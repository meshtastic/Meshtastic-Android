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
package org.meshtastic.core.repository

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Decodes [MeshLogPrefs.retentionDays], which overloads two sentinels onto the day count: [KEEP_FOREVER] never prunes
 * and [ONE_HOUR] keeps only the last hour.
 *
 * Both sentinels are real user selections rather than "unset", so pruning callers must resolve the window here instead
 * of treating the setting as a plain day count.
 */
object MeshLogRetention {

    /** Retention setting for "never delete". */
    const val KEEP_FOREVER: Int = 0

    /** Retention setting for "keep the last hour", which cannot be expressed as a whole number of days. */
    const val ONE_HOUR: Int = -1

    /** The retention window for [retentionDays], or `null` when logs are never pruned. */
    fun windowOrNull(retentionDays: Int): Duration? = when {
        retentionDays == KEEP_FOREVER -> null

        // Every negative value resolves to the one-hour sentinel; scaling one by days would put the cutoff in the
        // future, matching the whole table.
        retentionDays < 0 -> 1.hours

        else -> retentionDays.days
    }
}
