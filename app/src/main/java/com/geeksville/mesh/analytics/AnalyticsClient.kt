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
