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

@file:Suppress("MagicNumber")

package org.meshtastic.feature.settings.util

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.interval_always_on
import org.meshtastic.core.strings.interval_unset
import org.meshtastic.core.strings.plurals_hours
import org.meshtastic.core.strings.plurals_minutes
import org.meshtastic.core.strings.plurals_seconds
import java.util.concurrent.TimeUnit

/**
 * Defines a set of fixed time intervals in seconds, commonly used for configuration settings.
 *
 * @param value The interval duration in seconds.
 * @param textRes The string resource for the display name of the interval.
 */
enum class FixedUpdateIntervals(
    val value: Long,
    val textRes: StringResource? = null,
    val pluralRes: PluralStringResource? = null,
    val quantity: Int? = null,
) {
    UNSET(0L, textRes = Res.string.interval_unset),
    ONE_SECOND(1L, pluralRes = Res.plurals.plurals_seconds, quantity = 1),
    TWO_SECONDS(2L, pluralRes = Res.plurals.plurals_seconds, quantity = 2),
    THREE_SECONDS(3L, pluralRes = Res.plurals.plurals_seconds, quantity = 3),
    FOUR_SECONDS(4L, pluralRes = Res.plurals.plurals_seconds, quantity = 4),
    FIVE_SECONDS(5L, pluralRes = Res.plurals.plurals_seconds, quantity = 5),
    TEN_SECONDS(10L, pluralRes = Res.plurals.plurals_seconds, quantity = 10),
    FIFTEEN_SECONDS(15L, pluralRes = Res.plurals.plurals_seconds, quantity = 15),
    TWENTY_SECONDS(20L, pluralRes = Res.plurals.plurals_seconds, quantity = 20),
    THIRTY_SECONDS(30L, pluralRes = Res.plurals.plurals_seconds, quantity = 30),
    FORTY_FIVE_SECONDS(45L, pluralRes = Res.plurals.plurals_seconds, quantity = 45),
    ONE_MINUTE(TimeUnit.MINUTES.toSeconds(1), pluralRes = Res.plurals.plurals_minutes, quantity = 1),
    TWO_MINUTES(TimeUnit.MINUTES.toSeconds(2), pluralRes = Res.plurals.plurals_minutes, quantity = 2),
    FIVE_MINUTES(TimeUnit.MINUTES.toSeconds(5), pluralRes = Res.plurals.plurals_minutes, quantity = 5),
    TEN_MINUTES(TimeUnit.MINUTES.toSeconds(10), pluralRes = Res.plurals.plurals_minutes, quantity = 10),
    FIFTEEN_MINUTES(TimeUnit.MINUTES.toSeconds(15), pluralRes = Res.plurals.plurals_minutes, quantity = 15),
    THIRTY_MINUTES(TimeUnit.MINUTES.toSeconds(30), pluralRes = Res.plurals.plurals_minutes, quantity = 30),
    ONE_HOUR(TimeUnit.HOURS.toSeconds(1), pluralRes = Res.plurals.plurals_hours, quantity = 1),
    TWO_HOURS(TimeUnit.HOURS.toSeconds(2), pluralRes = Res.plurals.plurals_hours, quantity = 2),
    THREE_HOURS(TimeUnit.HOURS.toSeconds(3), pluralRes = Res.plurals.plurals_hours, quantity = 3),
    FOUR_HOURS(TimeUnit.HOURS.toSeconds(4), pluralRes = Res.plurals.plurals_hours, quantity = 4),
    FIVE_HOURS(TimeUnit.HOURS.toSeconds(5), pluralRes = Res.plurals.plurals_hours, quantity = 5),
    SIX_HOURS(TimeUnit.HOURS.toSeconds(6), pluralRes = Res.plurals.plurals_hours, quantity = 6),
    TWELVE_HOURS(TimeUnit.HOURS.toSeconds(12), pluralRes = Res.plurals.plurals_hours, quantity = 12),
    EIGHTEEN_HOURS(TimeUnit.HOURS.toSeconds(18), pluralRes = Res.plurals.plurals_hours, quantity = 18),
    TWENTY_FOUR_HOURS(TimeUnit.HOURS.toSeconds(24), pluralRes = Res.plurals.plurals_hours, quantity = 24),
    THIRTY_SIX_HOURS(TimeUnit.HOURS.toSeconds(36), pluralRes = Res.plurals.plurals_hours, quantity = 36),
    FORTY_EIGHT_HOURS(TimeUnit.HOURS.toSeconds(48), pluralRes = Res.plurals.plurals_hours, quantity = 48),
    SEVENTY_TWO_HOURS(TimeUnit.HOURS.toSeconds(72), pluralRes = Res.plurals.plurals_hours, quantity = 72),
    ALWAYS_ON(Int.MAX_VALUE.toLong(), textRes = Res.string.interval_always_on),
    EIGHTY_SECONDS(TimeUnit.SECONDS.toSeconds(80), pluralRes = Res.plurals.plurals_seconds, quantity = 80),
    NINETY_SECONDS(TimeUnit.SECONDS.toSeconds(90), pluralRes = Res.plurals.plurals_seconds, quantity = 90),
    EIGHT_SECONDS(TimeUnit.SECONDS.toSeconds(8), pluralRes = Res.plurals.plurals_seconds, quantity = 8),
    FORTY_SECONDS(TimeUnit.SECONDS.toSeconds(40), pluralRes = Res.plurals.plurals_seconds, quantity = 40),
    ;

    companion object {
        /**
         * Finds a [FixedUpdateIntervals] that matches the given value.
         *
         * @return The corresponding [FixedUpdateIntervals] or null if no match is found.
         */
        fun fromValue(value: Long): FixedUpdateIntervals? = entries.find { it.value == value }
    }
}

/**
 * Represents a specific configuration context that determines a subset of allowed update intervals. This is used to
 * filter the available [FixedUpdateIntervals] for a particular setting.
 */
enum class IntervalConfiguration {
    ALL,
    BROADCAST_SHORT,
    BROADCAST_MEDIUM,
    BROADCAST_LONG,
    NODE_INFO_BROADCAST,
    DETECTION_SENSOR_MINIMUM,
    DETECTION_SENSOR_STATE,
    NAG_TIMEOUT,
    OUTPUT,
    PAX_COUNTER,
    POSITION,
    POSITION_BROADCAST,
    GPS_UPDATE,
    RANGE_TEST_SENDER,
    SMART_BROADCAST_MINIMUM,
    DISPLAY_SCREEN_ON,
    DISPLAY_CAROUSEL,
    ;

    /** A list of [FixedUpdateIntervals] that are permissible for this configuration. */
    val allowedIntervals: List<FixedUpdateIntervals> by lazy {
        when (this) {
            ALL -> FixedUpdateIntervals.entries
            BROADCAST_SHORT ->
                listOf(
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                    FixedUpdateIntervals.TWO_HOURS,
                    FixedUpdateIntervals.THREE_HOURS,
                    FixedUpdateIntervals.FOUR_HOURS,
                    FixedUpdateIntervals.FIVE_HOURS,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.EIGHTEEN_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                    FixedUpdateIntervals.THIRTY_SIX_HOURS,
                    FixedUpdateIntervals.FORTY_EIGHT_HOURS,
                    FixedUpdateIntervals.SEVENTY_TWO_HOURS,
                )
            BROADCAST_MEDIUM ->
                listOf(
                    FixedUpdateIntervals.ONE_HOUR,
                    FixedUpdateIntervals.TWO_HOURS,
                    FixedUpdateIntervals.THREE_HOURS,
                    FixedUpdateIntervals.FOUR_HOURS,
                    FixedUpdateIntervals.FIVE_HOURS,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.EIGHTEEN_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                    FixedUpdateIntervals.THIRTY_SIX_HOURS,
                    FixedUpdateIntervals.FORTY_EIGHT_HOURS,
                    FixedUpdateIntervals.SEVENTY_TWO_HOURS,
                )
            BROADCAST_LONG ->
                listOf(
                    FixedUpdateIntervals.THREE_HOURS,
                    FixedUpdateIntervals.FOUR_HOURS,
                    FixedUpdateIntervals.FIVE_HOURS,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.EIGHTEEN_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                    FixedUpdateIntervals.THIRTY_SIX_HOURS,
                    FixedUpdateIntervals.FORTY_EIGHT_HOURS,
                    FixedUpdateIntervals.SEVENTY_TWO_HOURS,
                )
            NODE_INFO_BROADCAST ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.THREE_HOURS,
                    FixedUpdateIntervals.FOUR_HOURS,
                    FixedUpdateIntervals.FIVE_HOURS,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.EIGHTEEN_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                    FixedUpdateIntervals.THIRTY_SIX_HOURS,
                    FixedUpdateIntervals.FORTY_EIGHT_HOURS,
                    FixedUpdateIntervals.SEVENTY_TWO_HOURS,
                )
            OUTPUT ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.ONE_SECOND,
                    FixedUpdateIntervals.TWO_SECONDS,
                    FixedUpdateIntervals.THREE_SECONDS,
                    FixedUpdateIntervals.FOUR_SECONDS,
                    FixedUpdateIntervals.FIVE_SECONDS,
                    FixedUpdateIntervals.TEN_SECONDS,
                )
            DETECTION_SENSOR_MINIMUM ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.FIFTEEN_SECONDS,
                    FixedUpdateIntervals.THIRTY_SECONDS,
                    FixedUpdateIntervals.ONE_MINUTE,
                    FixedUpdateIntervals.TWO_MINUTES,
                    FixedUpdateIntervals.FIVE_MINUTES,
                    FixedUpdateIntervals.TEN_MINUTES,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                    FixedUpdateIntervals.TWO_HOURS,
                    FixedUpdateIntervals.THREE_HOURS,
                    FixedUpdateIntervals.FOUR_HOURS,
                    FixedUpdateIntervals.FIVE_HOURS,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.EIGHTEEN_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                    FixedUpdateIntervals.THIRTY_SIX_HOURS,
                    FixedUpdateIntervals.FORTY_EIGHT_HOURS,
                    FixedUpdateIntervals.SEVENTY_TWO_HOURS,
                )
            DETECTION_SENSOR_STATE ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                    FixedUpdateIntervals.TWO_HOURS,
                    FixedUpdateIntervals.THREE_HOURS,
                    FixedUpdateIntervals.FOUR_HOURS,
                    FixedUpdateIntervals.FIVE_HOURS,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.EIGHTEEN_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                    FixedUpdateIntervals.THIRTY_SIX_HOURS,
                    FixedUpdateIntervals.FORTY_EIGHT_HOURS,
                    FixedUpdateIntervals.SEVENTY_TWO_HOURS,
                )
            NAG_TIMEOUT ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.ONE_SECOND,
                    FixedUpdateIntervals.FIVE_SECONDS,
                    FixedUpdateIntervals.TEN_SECONDS,
                    FixedUpdateIntervals.FIFTEEN_SECONDS,
                    FixedUpdateIntervals.THIRTY_SECONDS,
                    FixedUpdateIntervals.ONE_MINUTE,
                )
            PAX_COUNTER ->
                listOf(
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                    FixedUpdateIntervals.TWO_HOURS,
                    FixedUpdateIntervals.THREE_HOURS,
                    FixedUpdateIntervals.FOUR_HOURS,
                    FixedUpdateIntervals.FIVE_HOURS,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.EIGHTEEN_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                    FixedUpdateIntervals.THIRTY_SIX_HOURS,
                    FixedUpdateIntervals.FORTY_EIGHT_HOURS,
                    FixedUpdateIntervals.SEVENTY_TWO_HOURS,
                )
            POSITION ->
                listOf(
                    FixedUpdateIntervals.ONE_SECOND,
                    FixedUpdateIntervals.TWO_SECONDS,
                    FixedUpdateIntervals.FIVE_SECONDS,
                    FixedUpdateIntervals.TEN_SECONDS,
                    FixedUpdateIntervals.FIFTEEN_SECONDS,
                    FixedUpdateIntervals.TWENTY_SECONDS,
                    FixedUpdateIntervals.THIRTY_SECONDS,
                    FixedUpdateIntervals.FORTY_FIVE_SECONDS,
                    FixedUpdateIntervals.ONE_MINUTE,
                    FixedUpdateIntervals.TWO_MINUTES,
                    FixedUpdateIntervals.FIVE_MINUTES,
                    FixedUpdateIntervals.TEN_MINUTES,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                )
            POSITION_BROADCAST ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.ONE_MINUTE,
                    FixedUpdateIntervals.NINETY_SECONDS,
                    FixedUpdateIntervals.FIVE_MINUTES,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                    FixedUpdateIntervals.TWO_HOURS,
                    FixedUpdateIntervals.THREE_HOURS,
                    FixedUpdateIntervals.FOUR_HOURS,
                    FixedUpdateIntervals.FIVE_HOURS,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.EIGHTEEN_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                    FixedUpdateIntervals.THIRTY_SIX_HOURS,
                    FixedUpdateIntervals.FORTY_EIGHT_HOURS,
                    FixedUpdateIntervals.SEVENTY_TWO_HOURS,
                )
            GPS_UPDATE ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.EIGHT_SECONDS,
                    FixedUpdateIntervals.TWENTY_SECONDS,
                    FixedUpdateIntervals.FORTY_SECONDS,
                    FixedUpdateIntervals.ONE_MINUTE,
                    FixedUpdateIntervals.EIGHTY_SECONDS,
                    FixedUpdateIntervals.TWO_MINUTES,
                    FixedUpdateIntervals.FIVE_MINUTES,
                    FixedUpdateIntervals.TEN_MINUTES,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                    FixedUpdateIntervals.SIX_HOURS,
                    FixedUpdateIntervals.TWELVE_HOURS,
                    FixedUpdateIntervals.TWENTY_FOUR_HOURS,
                )
            RANGE_TEST_SENDER ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.FIFTEEN_SECONDS,
                    FixedUpdateIntervals.THIRTY_SECONDS,
                    FixedUpdateIntervals.FORTY_FIVE_SECONDS,
                    FixedUpdateIntervals.ONE_MINUTE,
                    FixedUpdateIntervals.FIVE_MINUTES,
                    FixedUpdateIntervals.TEN_MINUTES,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                )
            SMART_BROADCAST_MINIMUM ->
                listOf(
                    FixedUpdateIntervals.FIFTEEN_SECONDS,
                    FixedUpdateIntervals.THIRTY_SECONDS,
                    FixedUpdateIntervals.FORTY_FIVE_SECONDS,
                    FixedUpdateIntervals.ONE_MINUTE,
                    FixedUpdateIntervals.FIVE_MINUTES,
                    FixedUpdateIntervals.TEN_MINUTES,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                )

            DISPLAY_SCREEN_ON ->
                listOf(
                    FixedUpdateIntervals.FIFTEEN_SECONDS,
                    FixedUpdateIntervals.THIRTY_SECONDS,
                    FixedUpdateIntervals.ONE_MINUTE,
                    FixedUpdateIntervals.FIVE_MINUTES,
                    FixedUpdateIntervals.TEN_MINUTES,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                    FixedUpdateIntervals.THIRTY_MINUTES,
                    FixedUpdateIntervals.ONE_HOUR,
                    FixedUpdateIntervals.ALWAYS_ON,
                )

            DISPLAY_CAROUSEL ->
                listOf(
                    FixedUpdateIntervals.UNSET,
                    FixedUpdateIntervals.FIFTEEN_SECONDS,
                    FixedUpdateIntervals.THIRTY_SECONDS,
                    FixedUpdateIntervals.ONE_MINUTE,
                    FixedUpdateIntervals.FIVE_MINUTES,
                    FixedUpdateIntervals.TEN_MINUTES,
                    FixedUpdateIntervals.FIFTEEN_MINUTES,
                )
        }
    }
}

/**
 * Represents an update interval, which can be either a predefined fixed value or a custom manual value in seconds. This
 * is a type-safe representation for settings that involve time durations.
 */
sealed class UpdateInterval {
    /** The duration of the interval in seconds. */
    abstract val value: Long

    /** A unique, stable identifier for this interval, suitable for use in Compose keys. */
    val id: String
        get() =
            when (this) {
                is Fixed -> "fixed_$value"
                is Manual -> "manual_$value"
            }

    /** A predefined, fixed interval. */
    data class Fixed(val interval: FixedUpdateIntervals) : UpdateInterval() {
        override val value: Long = interval.value
    }

    /** A user-defined interval, specified in seconds. */
    data class Manual(override val value: Long) : UpdateInterval()

    companion object {
        /**
         * Creates an [UpdateInterval] from a raw Long value in seconds. If the value matches a predefined
         * [FixedUpdateIntervals], a [Fixed] instance is returned. Otherwise, a [Manual] instance is returned.
         *
         * @param value The interval duration in seconds.
         */
        fun fromValue(value: Long): UpdateInterval =
            FixedUpdateIntervals.fromValue(value)?.let { Fixed(it) } ?: Manual(value)
    }
}
