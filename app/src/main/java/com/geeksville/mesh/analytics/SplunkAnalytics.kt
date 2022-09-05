package com.geeksville.mesh.analytics


/**
 * Created by kevinh on 12/24/14.
 */

// Mint.initAndStartSession(MyActivity.this, "01a9c628");

/* disable for now because something in gradle doesn't like their lib repo
import com.splunk.mint.Mint

class SplunkAnalytics(val context: Context, apiToken: String) : AnalyticsProvider, Logging {

    private var inited = false

    init {
        try {
            Mint.initAndStartSession(context, apiToken)
            inited = true
        } catch(ex: Exception) {
            error("exception logging failed to init")
        }
    }

    override fun endSession() {
        track("End Session")
        if (inited)
            Mint.closeSession(context)
        // Mint.flush() // Send results now
    }

    override fun trackLowValue(event: String, vararg properties: DataPair) {
    }

    override fun track(event: String, vararg properties: DataPair) {
        if (inited)
            Mint.logEvent(event)
    }

    override fun startSession() {
        if (inited) {
            Mint.startSession(context)
            track("Start Session")
        }
    }

    override fun setUserInfo(vararg p: DataPair) {
        if (inited)
            p.forEach { Mint.addExtraData(it.name, it.value.toString()) }
    }

    override fun increment(name: String, amount: Double) {
        if (inited)
            Mint.logEvent("$name increment")
    }

    override fun sendScreenView(name: String) {
    }

    override fun endScreenView() {
    }
}

 */