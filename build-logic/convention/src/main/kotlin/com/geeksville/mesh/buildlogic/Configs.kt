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

package com.geeksville.mesh.buildlogic

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

object Configs {
    const val APPLICATION_ID = "com.geeksville.mesh"
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
    const val COMPILE_SDK = 36


    /**
     * This version string serves as a fallback for local development environments
     * where the version cannot be automatically determined from Git tags (e.g., a fresh clone
     * without fetching tags, or if Git is not accessible).
     *
     * On CI servers, the version is dynamically generated based on the current Git tag.
     *
     * Before creating a new release, this value should be updated manually
     * to reflect the version number of the Git tag that will be created for that release.
     * @see [RELEASE_PROCESS.md]
     */
    const val VERSION_NAME_BASE = "2.7.1"
    const val MIN_FW_VERSION = "2.5.14" // Minimum device firmware version supported by this app
    const val ABS_MIN_FW_VERSION = "2.3.15" // Minimum device firmware version supported by this app
}
