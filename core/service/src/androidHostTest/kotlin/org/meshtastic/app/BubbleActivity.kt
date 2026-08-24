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

/**
 * Test-only stub for the real `BubbleActivity` in the `:androidApp` module, mirroring the [MainActivity] stub next to
 * it. `MeshNotificationManagerImpl` resolves the bubble host by FQN via `Class.forName(...)` so `:core:service` does
 * not depend on `:androidApp`; this lets unit tests build the bubble `PendingIntent` without that dependency.
 */
class BubbleActivity : Activity()
