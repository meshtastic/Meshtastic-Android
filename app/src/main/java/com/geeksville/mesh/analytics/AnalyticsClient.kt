/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.analytics

/**
 * Created by kevinh on 12/24/14.
 */
interface AnalyticsProvider {

    // Turn analytics logging on/off
    fun setEnabled(on: Boolean)

    /**
     * Store an event
     */
    fun track(event: String, vararg properties: DataPair)

    /**
     * Only track this event if using a cheap provider (like google)
     */
    fun trackLowValue(event: String, vararg properties: DataPair)

    fun endSession()
    fun startSession()

    /**
     * Set persistent ID info about this user, as a key value pair
     */
    fun setUserInfo(vararg p: DataPair)

    /**
     * Increment some sort of analytics counter
     */
    fun increment(name: String, amount: Double = 1.0)

    fun sendScreenView(name: String)
    fun endScreenView()
}
