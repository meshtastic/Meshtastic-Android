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
package org.meshtastic.feature.settings.util

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.interval_unset
import org.meshtastic.core.resources.plurals_milliseconds
import org.meshtastic.core.resources.plurals_seconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long the external notification output stays driven, in **milliseconds**, which is the unit
 * `ModuleConfig.ExternalNotificationConfig.output_ms` is read in. [FixedUpdateIntervals] cannot serve this field: its
 * values are seconds, and writing one here sets a pulse a thousand times shorter than the label promises.
 *
 * The sub-second entries are the durations the firmware itself defaults to, so a radio that has never been configured
 * from this app still matches a row instead of reading as unset.
 */
enum class FixedOutputDurations(
    val value: Long,
    val textRes: StringResource? = null,
    val pluralRes: PluralStringResource? = null,
    val quantity: Int? = null,
) {
    UNSET(0L, textRes = Res.string.interval_unset),
    ONE_HUNDRED_MILLISECONDS(
        100.milliseconds.inWholeMilliseconds,
        pluralRes = Res.plurals.plurals_milliseconds,
        quantity = 100,
    ),
    FIVE_HUNDRED_MILLISECONDS(
        500.milliseconds.inWholeMilliseconds,
        pluralRes = Res.plurals.plurals_milliseconds,
        quantity = 500,
    ),
    ONE_SECOND(1.seconds.inWholeMilliseconds, pluralRes = Res.plurals.plurals_seconds, quantity = 1),
    TWO_SECONDS(2.seconds.inWholeMilliseconds, pluralRes = Res.plurals.plurals_seconds, quantity = 2),
    THREE_SECONDS(3.seconds.inWholeMilliseconds, pluralRes = Res.plurals.plurals_seconds, quantity = 3),
    FOUR_SECONDS(4.seconds.inWholeMilliseconds, pluralRes = Res.plurals.plurals_seconds, quantity = 4),
    FIVE_SECONDS(5.seconds.inWholeMilliseconds, pluralRes = Res.plurals.plurals_seconds, quantity = 5),
    TEN_SECONDS(10.seconds.inWholeMilliseconds, pluralRes = Res.plurals.plurals_seconds, quantity = 10),
    ;

    companion object {
        /** The durations the picker offers, in order. */
        val allowed: List<FixedOutputDurations> = entries
    }
}
