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
 * The filter previously pointed at `com.atakmap.app.component`, a class that does not exist in this APK, so any process
 * that launched it — and it is exported, so any app could — crashed Meshtastic on activity instantiation. This class
 * exists purely so the advertised component resolves to something real. It shows no UI and finishes immediately.
 *
 * The marker is believed to be discovery-only (enumerated via `queryIntentActivities`, never started), which is why
 * renaming the target should be safe. **That has not been verified against a real ATAK install**, and nothing in this
 * repository documents ATAK's matching behaviour — see the manifest comment. Note also that no `plugin-api` meta-data
 * is declared anywhere here, so this app is not an ATAK plugin and the marker may simply be vestigial; TAK interop
 * actually runs over the local CoT server and `AtakFileWriter`.
 */
class AtakPluginDiscoveryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
