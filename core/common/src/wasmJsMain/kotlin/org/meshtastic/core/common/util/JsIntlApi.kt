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
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:Suppress("UnusedParameter", "TooManyFunctions")

package org.meshtastic.core.common.util

// Raw Intl/browser JS interop, isolated to this one file (mirrors core:ble's WebBluetoothApi.kt).
internal external interface JsNumberFormat : JsAny {
    fun format(value: Double): String
}

internal external interface JsDateTimeFormat : JsAny {
    fun format(value: Double): String
}

internal external interface JsRelativeTimeFormat : JsAny {
    fun format(value: Double, unit: String): String
}

/** `navigator.language`, a BCP-47 tag like `"en-US"`, or `""` when unavailable. */
internal fun browserLanguage(): String = js("(navigator.language || '')")

// `locale || undefined` falls through to Intl's default-locale resolution for an empty tag.
private fun newNumberFormat(locale: String, options: JsAny): JsNumberFormat =
    js("new Intl.NumberFormat(locale || undefined, options)")

private fun newDateTimeFormat(locale: String, options: JsAny): JsDateTimeFormat =
    js("new Intl.DateTimeFormat(locale || undefined, options)")

private fun newRelativeTimeFormat(locale: String, options: JsAny): JsRelativeTimeFormat =
    js("new Intl.RelativeTimeFormat(locale || undefined, options)")

// roundingMode (ES2023) is ignored, not an error, on engines that predate it.
private fun decimalOptions(fractionDigits: Int): JsAny =
    js("({ minimumFractionDigits: fractionDigits, maximumFractionDigits: fractionDigits, roundingMode: 'halfExpand' })")

private fun unitOptions(unit: String): JsAny = js("({ style: 'unit', unit: unit, unitDisplay: 'short' })")

// Mirrors the JVM/Android actuals' integer-precision elevation formatter (`Precision.integer()`).
private fun unitIntegerOptions(unit: String): JsAny =
    js("({ style: 'unit', unit: unit, unitDisplay: 'short', maximumFractionDigits: 0 })")

private fun dateStyleOptions(style: String): JsAny = js("({ dateStyle: style })")

private fun timeStyleOptions(style: String): JsAny = js("({ timeStyle: style })")

private fun dateTimeStyleOptions(dateStyle: String, timeStyle: String): JsAny =
    js("({ dateStyle: dateStyle, timeStyle: timeStyle })")

private fun relativeTimeOptions(): JsAny = js("({ numeric: 'auto' })")

/** A locale-aware decimal formatter with a fixed number of fraction digits, rounding half-up. */
internal fun decimalFormatter(locale: String, fractionDigits: Int): JsNumberFormat =
    newNumberFormat(locale, decimalOptions(fractionDigits))

/** A locale-aware formatter for [unit] (an `Intl.NumberFormat` `unit` identifier, e.g. `"kilometer-per-hour"`). */
internal fun unitFormatter(locale: String, unit: String): JsNumberFormat = newNumberFormat(locale, unitOptions(unit))

/** Same as [unitFormatter], but forced to integer precision -- for values that must never show a decimal. */
internal fun unitIntegerFormatter(locale: String, unit: String): JsNumberFormat =
    newNumberFormat(locale, unitIntegerOptions(unit))

/** A date-only formatter at the given `Intl.DateTimeFormat` `dateStyle` (`"full"|"long"|"medium"|"short"`). */
internal fun dateOnlyFormatter(locale: String, style: String): JsDateTimeFormat =
    newDateTimeFormat(locale, dateStyleOptions(style))

/** A time-only formatter at the given `Intl.DateTimeFormat` `timeStyle`. */
internal fun timeOnlyFormatter(locale: String, style: String): JsDateTimeFormat =
    newDateTimeFormat(locale, timeStyleOptions(style))

/** A combined date-and-time formatter at the given `dateStyle`/`timeStyle` pair. */
internal fun dateTimeFormatter(locale: String, dateStyle: String, timeStyle: String): JsDateTimeFormat =
    newDateTimeFormat(locale, dateTimeStyleOptions(dateStyle, timeStyle))

/** `numeric: 'auto'` so small deltas render as "now"/"yesterday" where CLDR has a word for them. */
internal fun relativeTimeFormatter(locale: String): JsRelativeTimeFormat =
    newRelativeTimeFormat(locale, relativeTimeOptions())
