package com.geeksville.mesh.analytics

/**
 * Created by kevinh on 1/12/16.
 */
class TeeAnalytics(vararg providersIn: AnalyticsProvider) : AnalyticsProvider {

    val providers = providersIn

    override fun track(event: String, vararg properties: DataPair) {
        providers.forEach { it.track(event, *properties) }
    }

    override fun trackLowValue(event: String, vararg properties: DataPair) {
        providers.forEach { it.trackLowValue(event, *properties) }
    }

    override fun endSession() {
        providers.forEach { it.endSession() }
    }

    override fun increment(name: String, amount: Double) {
        providers.forEach { it.increment(name, amount) }
    }

    override fun setUserInfo(vararg p: DataPair) {
        providers.forEach { it.setUserInfo(*p) }
    }

    override fun startSession() {
        providers.forEach { it.startSession() }
    }

    override fun sendScreenView(name: String) {
        providers.forEach { it.sendScreenView(name) }
    }

    override fun endScreenView() {
        providers.forEach { it.endScreenView() }
    }

    override fun setEnabled(on: Boolean) {
        providers.forEach { it.setEnabled(on) }
    }
}