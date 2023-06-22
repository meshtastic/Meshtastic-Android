package com.geeksville.mesh.analytics

import android.content.Context
import android.os.Bundle
import com.geeksville.mesh.android.AppPrefs
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging

/**
 * Implement our analytics API using Firebase Analytics
 */
class GoogleAnalytics(context: Context) : AnalyticsProvider, Logging {

    val t = com.google.firebase.analytics.FirebaseAnalytics.getInstance(context)

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
        GeeksvilleApplication.currentActivity?.let {
            t.setCurrentScreen(
                it, name, null
            )
        }
    }

    override fun endScreenView() {
        // debug("Analytics: end screen")
    }
}