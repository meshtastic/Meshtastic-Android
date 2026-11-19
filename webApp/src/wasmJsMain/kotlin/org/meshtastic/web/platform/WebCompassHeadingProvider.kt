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
    // Every js("...") snippet references its Kotlin parameters inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.web.platform

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.feature.node.compass.HeadingState

/**
 * The magnetic heading from the browser's orientation events: `deviceorientationabsolute` on Chrome, which reports
 * `alpha` against magnetic north, and Safari's `webkitCompassHeading`. Safari sends neither until the page calls
 * `DeviceOrientationEvent.requestPermission()` from a user gesture, so there the next tap anywhere asks.
 *
 * It starts with a heading-less state so the compass shows distance and bearing before the first reading, and reports
 * no sensor where the browser has no orientation events, the user refuses Safari's prompt, or no reading arrives.
 */
class WebCompassHeadingProvider : CompassHeadingProvider {
    override fun headingUpdates(): Flow<HeadingState> = callbackFlow {
        trySend(HeadingState())
        val watch =
            watchCompass(
                onHeading = { heading, accuracy ->
                    trySend(HeadingState(heading = heading.toFloat(), accuracy = accuracy))
                },
                onUnavailable = { trySend(HeadingState(hasSensor = false)) },
                noReadingTimeoutMs = NO_READING_TIMEOUT_MS,
            )
        awaitClose { stopCompass(watch) }
    }

    private companion object {
        /** Chrome on a computer without sensors registers the event and never fires it. */
        const val NO_READING_TIMEOUT_MS = 3_000
    }
}

/**
 * Returns a handle for [stopCompass]. [onHeading] gets degrees clockwise from magnetic north and an accuracy on
 * Android's `SensorManager.SENSOR_STATUS_*` scale: 3 high, 2 medium, 1 low, 0 unreliable.
 */
private fun watchCompass(onHeading: (Double, Int) -> Unit, onUnavailable: () -> Unit, noReadingTimeoutMs: Int): JsAny =
    js(
        """(() => {
        const watch = { listeners: [], timer: null };
        const listen = (target, type, handler, options) => {
            target.addEventListener(type, handler, options);
            watch.listeners.push(() => target.removeEventListener(type, handler, options));
        };
        if (typeof DeviceOrientationEvent === 'undefined') {
            onUnavailable();
            return watch;
        }
        let heard = false;
        const onOrientation = (event) => {
            let heading = null;
            let accuracy = 3;
            if (typeof event.webkitCompassHeading === 'number' && !isNaN(event.webkitCompassHeading)) {
                heading = event.webkitCompassHeading;
                const degrees = event.webkitCompassAccuracy;
                accuracy = degrees < 0 ? 0 : degrees <= 10 ? 3 : degrees <= 25 ? 2 : 1;
            } else if (event.absolute && typeof event.alpha === 'number') {
                heading = (360 - event.alpha) % 360;
            }
            if (heading === null) return;
            heard = true;
            onHeading(heading, accuracy);
        };
        const absolute = 'ondeviceorientationabsolute' in window;
        listen(window, absolute ? 'deviceorientationabsolute' : 'deviceorientation', onOrientation);

        const startTimer = () => {
            watch.timer = setTimeout(() => { if (!heard) onUnavailable(); }, noReadingTimeoutMs);
        };
        if (typeof DeviceOrientationEvent.requestPermission === 'function') {
            const ask = () => {
                watch.listeners.splice(1).forEach((remove) => remove());
                if (heard) return;
                DeviceOrientationEvent.requestPermission()
                    .then((answer) => { if (answer === 'granted') startTimer(); else onUnavailable(); })
                    .catch(() => onUnavailable());
            };
            listen(document, 'click', ask, true);
            listen(document, 'touchend', ask, true);
        } else {
            startTimer();
        }
        return watch;
    })()""",
    )

private fun stopCompass(watch: JsAny): Unit = js(
    """{
        clearTimeout(watch.timer);
        watch.listeners.forEach((remove) => remove());
    }""",
)
