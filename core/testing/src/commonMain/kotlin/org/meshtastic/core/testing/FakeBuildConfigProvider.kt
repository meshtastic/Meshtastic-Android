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
package org.meshtastic.core.testing

import org.meshtastic.core.common.BuildConfigProvider

/** A [BuildConfigProvider] with plausible values, for code that only needs to name the build it is running in. */
class FakeBuildConfigProvider(
    override val isDebug: Boolean = true,
    override val applicationId: String = "org.meshtastic.app",
    override val versionCode: Int = 1,
    override val versionName: String = "1.0.0",
    override val absoluteMinFwVersion: String = "2.0.0",
    override val minFwVersion: String = "2.0.0",
) : BuildConfigProvider
