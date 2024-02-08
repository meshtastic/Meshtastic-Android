package com.geeksville.mesh.analytics

import android.content.Context
import com.geeksville.mesh.android.Logging

class DataPair(val name: String, valueIn: Any?) {
    val value = valueIn ?: "null"

    /// An accumulating firebase event - only one allowed per event
    constructor(d: Double) : this("BOGUS", d)
    constructor(d: Int) : this("BOGUS", d)
}

/**
 * Implement our analytics API using Firebase Analytics
 */
@Suppress("UNUSED_PARAMETER")
class NopAnalytics(context: Context) : AnalyticsProvider, Logging {

    init {
    }

    override fun setEnabled(on: Boolean) {
    }

    override fun endSession() {
    }

    override fun trackLowValue(event: String, vararg properties: DataPair) {
    }

    override fun track(event: String, vararg properties: DataPair) {
    }

    override fun startSession() {
    }

    override fun setUserInfo(vararg p: DataPair) {
    }

    override fun increment(name: String, amount: Double) {
    }

    /**
     * Send a google analytics screen view event
     */
    override fun sendScreenView(name: String) {
    }

    override fun endScreenView() {
    }
}
