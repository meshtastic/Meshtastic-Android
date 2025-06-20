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
    const val MIN_SDK_VERSION = 26
    const val TARGET_SDK = 36
    const val COMPILE_SDK = 36
    const val VERSION_CODE = 30622 // format is Mmmss (where M is 1+the numeric major number
    const val VERSION_NAME = "2.6.22"
    const val USE_CRASHLYTICS = false // Set to true if you want to use Firebase Crashlytics
    const val MIN_FW_VERSION = "2.5.14" // Minimum device firmware version supported by this app
    const val ABS_MIN_FW_VERSION = "2.3.15" // Minimum device firmware version supported by this app
}
