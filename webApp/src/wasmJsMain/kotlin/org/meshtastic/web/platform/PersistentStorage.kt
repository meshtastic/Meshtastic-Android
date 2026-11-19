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
package org.meshtastic.web.platform

import co.touchlab.kermit.Logger

/**
 * Asks the browser to keep this origin's storage, where the OPFS database lives, out of eviction under storage
 * pressure. Chrome decides from engagement, installation and the notification permission and may say no at first, so a
 * refusal asks again on the next user gesture, once; Firefox shows its own prompt there.
 */
internal fun requestPersistentStorage() =
    persistStorage(onResult = { outcome -> Logger.i { "Persistent storage: $outcome" } })

/** Reports `already`, `granted`, `denied` or `unsupported`, and a second outcome after the gesture retry. */
@Suppress("UnusedParameter") // read by name inside the js() body
private fun persistStorage(onResult: (String) -> Unit): Unit =
    js(
        """{
        if (!navigator.storage || typeof navigator.storage.persist !== 'function') {
            onResult('unsupported');
            return;
        }
        navigator.storage.persisted().then((persisted) => {
            if (persisted) {
                onResult('already');
                return;
            }
            navigator.storage.persist().then((granted) => {
                onResult(granted ? 'granted' : 'denied');
                if (granted) return;
                const retry = () => {
                    removeEventListener('pointerdown', retry, true);
                    removeEventListener('keydown', retry, true);
                    navigator.storage.persist().then((again) => onResult(again ? 'granted after a gesture' : 'denied after a gesture'));
                };
                addEventListener('pointerdown', retry, true);
                addEventListener('keydown', retry, true);
            });
        }).catch((e) => onResult('failed: ' + e));
    }""",
    )
