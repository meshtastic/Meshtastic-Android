package com.geeksville.mesh.analytics

import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Created by kevinh on 12/24/14.
 */
class DataPair(val name: String, valueIn: Any?) {
    val value = valueIn ?: "null"

    /// An accumulating firebase event - only one allowed per event
    constructor(d: Double) : this(FirebaseAnalytics.Param.VALUE, d)
    constructor(d: Int) : this(FirebaseAnalytics.Param.VALUE, d)
}

public interface AnalyticsProvider {

    // Turn analytics logging on/off
    fun setEnabled(on: Boolean)

    /**
     * Store an event
     */
    fun track(event: String, vararg properties: DataPair): Unit

    /**
     * Only track this event if using a cheap provider (like google)
     */
    fun trackLowValue(event: String, vararg properties: DataPair): Unit

    fun endSession(): Unit
    fun startSession(): Unit

    /**
     * Set persistent ID info about this user, as a key value pair
     */
    fun setUserInfo(vararg p: DataPair)

    /**
     * Increment some sort of anyalytics counter
     */
    fun increment(name: String, amount: Double = 1.0)

    fun sendScreenView(name: String)
    fun endScreenView()

}


