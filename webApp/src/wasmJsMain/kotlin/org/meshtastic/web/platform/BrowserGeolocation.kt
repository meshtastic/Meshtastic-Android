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
@file:Suppress(
    // The browser surface this file wraps takes one small interop function per JS call.
    "TooManyFunctions",
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.web.platform

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.meshtastic.core.repository.Location
import org.meshtastic.core.ui.util.browserPermissionState
import kotlin.coroutines.resume

/** `navigator.geolocation.watchPosition` for as long as the flow is collected. */
internal fun watchGeolocation(): Flow<GeoReading> = callbackFlow {
    val watchId =
        watchPosition(onFix = { trySend(it.toFix()) }, onError = { trySend(readingForError(it)) }, maxAgeMs = 0)
    awaitClose { clearWatch(watchId) }
}

/** One fix, or the reason there is none. The browser shows its location prompt here if the page has not asked. */
internal suspend fun currentGeolocation(): GeoReading = suspendCancellableCoroutine { cont ->
    getCurrentPosition(
        onFix = { if (cont.isActive) cont.resume(it.toFix()) },
        onError = { if (cont.isActive) cont.resume(readingForError(it)) },
        timeoutMs = CURRENT_POSITION_TIMEOUT_MS,
        maxAgeMs = CURRENT_POSITION_MAX_AGE_MS,
    )
}

/**
 * The page's geolocation permission as the Permissions API reports it (`granted`, `prompt`, `denied`), followed for
 * changes. A browser without the Permissions API reports `granted`, so callers try and learn from the error instead.
 */
internal fun geolocationPermission(): Flow<String> = browserPermissionState("geolocation", unanswered = "granted")

/** One answer from the Geolocation API. */
internal sealed interface GeoReading {
    data class Fix(val location: Location, val verticalAccuracyM: Float?) : GeoReading

    /** The user or the browser refused location to this page. */
    data object Denied : GeoReading

    /** No fix could be had, or the browser has no Geolocation API. */
    data object Unavailable : GeoReading
}

private const val CURRENT_POSITION_TIMEOUT_MS = 30_000
private const val CURRENT_POSITION_MAX_AGE_MS = 10_000
private const val PERMISSION_DENIED = 1

private fun readingForError(code: Int): GeoReading =
    if (code == PERMISSION_DENIED) GeoReading.Denied else GeoReading.Unavailable

// The Geolocation spec gives altitude above the WGS84 ellipsoid, and heading as NaN when the device is still.
private fun JsAny.toFix(): GeoReading.Fix = GeoReading.Fix(
    location =
    Location(
        latitude = latitude(this),
        longitude = longitude(this),
        altitudeMeters = altitude(this)?.toDouble(),
        accuracyMeters = accuracy(this).toFloat(),
        speedMetersPerSecond = speed(this)?.toDouble()?.toFloat(),
        bearingDegrees = heading(this)?.toDouble()?.takeUnless { it.isNaN() }?.toFloat(),
        timeMillis = timestamp(this).toLong(),
    ),
    verticalAccuracyM = altitudeAccuracy(this)?.toDouble()?.toFloat(),
)

private fun watchPosition(onFix: (JsAny) -> Unit, onError: (Int) -> Unit, maxAgeMs: Int): Int = js(
    """{
    if (!navigator.geolocation) { onError(2); return -1; }
    return navigator.geolocation.watchPosition(
        (p) => onFix(p),
        (e) => onError(e.code),
        { enableHighAccuracy: true, maximumAge: maxAgeMs });
}""",
)

private fun clearWatch(watchId: Int) {
    js("{ if (watchId >= 0) navigator.geolocation.clearWatch(watchId); }")
}

private fun getCurrentPosition(onFix: (JsAny) -> Unit, onError: (Int) -> Unit, timeoutMs: Int, maxAgeMs: Int) {
    js(
        """{
        if (!navigator.geolocation) { onError(2); return; }
        navigator.geolocation.getCurrentPosition(
            (p) => onFix(p),
            (e) => onError(e.code),
            { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: maxAgeMs });
    }""",
    )
}

private fun latitude(p: JsAny): Double = js("p.coords.latitude")

private fun longitude(p: JsAny): Double = js("p.coords.longitude")

private fun altitude(p: JsAny): JsNumber? = js("p.coords.altitude")

private fun altitudeAccuracy(p: JsAny): JsNumber? = js("p.coords.altitudeAccuracy")

private fun accuracy(p: JsAny): Double = js("p.coords.accuracy")

private fun speed(p: JsAny): JsNumber? = js("p.coords.speed")

private fun heading(p: JsAny): JsNumber? = js("p.coords.heading")

private fun timestamp(p: JsAny): Double = js("p.timestamp")
