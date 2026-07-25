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
package org.meshtastic.app

import android.app.Activity
import android.os.Bundle

/**
 * No-op target for the `com.atakmap.app.component` discovery marker in the manifest.
 *
 * ATAK finds Meshtastic by enumerating activities that declare that action via `queryIntentActivities`; it never calls
 * `startActivity` on the result. The intent filter therefore has to stay exported, but it previously pointed at
 * `com.atakmap.app.component` — a class that does not exist in this APK — so any process that *did* launch it crashed
 * the app on activity instantiation.
 *
 * This exists purely so the advertised component resolves to something real. It shows no UI and finishes immediately.
 */
class AtakPluginDiscoveryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
