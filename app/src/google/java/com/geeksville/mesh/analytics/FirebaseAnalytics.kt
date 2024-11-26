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

import android.content.Context
import android.os.Bundle
import com.geeksville.mesh.android.AppPrefs
import com.geeksville.mesh.android.Logging
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.Firebase

class DataPair(val name: String, valueIn: Any?) {
    val value = valueIn ?: "null"

    /// An accumulating firebase event - only one allowed per event
    constructor(d: Double) : this(FirebaseAnalytics.Param.VALUE, d)
    constructor(d: Int) : this(FirebaseAnalytics.Param.VALUE, d)
}

/**
 * Implement our analytics API using Firebase Analytics
 */
class FirebaseAnalytics(context: Context) : AnalyticsProvider, Logging {

    val t = Firebase.analytics

    init {
        val pref = AppPrefs(context)
        t.setUserId(pref.getInstallId())
    }

    override fun setEnabled(on: Boolean) {
        t.setAnalyticsCollectionEnabled(on)
    }

    override fun endSession() {
        track("End Session")
        // Mint.flush() // Send results now
    }

    override fun trackLowValue(event: String, vararg properties: DataPair) {
        track(event, *properties)
    }

    override fun track(event: String, vararg properties: DataPair) {
        debug("Analytics: track $event")

        val bundle = Bundle()
        properties.forEach {
            when (it.value) {
                is Double -> bundle.putDouble(it.name, it.value)
                is Int -> bundle.putLong(it.name, it.value.toLong())
                is Long -> bundle.putLong(it.name, it.value)
                is Float -> bundle.putDouble(it.name, it.value.toDouble())
                else -> bundle.putString(it.name, it.value.toString())
            }
        }
        t.logEvent(event, bundle)
    }

    override fun startSession() {
        debug("Analytics: start session")
        // automatic with firebase
    }

    override fun setUserInfo(vararg p: DataPair) {
        p.forEach { t.setUserProperty(it.name, it.value.toString()) }
    }

    override fun increment(name: String, amount: Double) {
        //Mint.logEvent("$name increment")
    }

    /**
     * Send a google analytics screen view event
     */
    override fun sendScreenView(name: String) {
        debug("Analytics: start screen $name")
        t.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, name)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        }
    }

    override fun endScreenView() {
        // debug("Analytics: end screen")
    }
}
