package com.geeksville.mesh.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.edit
import com.geeksville.mesh.analytics.AnalyticsProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight


fun isGooglePlayAvailable(context: Context): Boolean {
    val a = GoogleApiAvailabilityLight.getInstance()
    val r = a.isGooglePlayServicesAvailable(context)
    return r != ConnectionResult.SERVICE_MISSING && r != ConnectionResult.SERVICE_INVALID
}

/**
 * Created by kevinh on 1/4/15.
 */

open class GeeksvilleApplication : Application(), Logging {

    companion object {
        lateinit var analytics: AnalyticsProvider
        var currentActivity: Activity? = null
        private val backstack = mutableListOf<Activity>()
    }

    private val lifecycleCallbacks = object : ActivityLifecycleCallbacks {
        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (backstack.contains(activity)) backstack.remove(activity)
            currentActivity = backstack.lastOrNull()
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            backstack.add(activity)
            currentActivity = backstack.lastOrNull()
        }

        override fun onActivityResumed(activity: Activity) {
        }
    }

    /// Are we running inside the testlab?
    val isInTestLab: Boolean
        get() {
            val testLabSetting =
                Settings.System.getString(contentResolver, "firebase.test.lab") ?: null
            if(testLabSetting != null)
                info("Testlab is $testLabSetting")
            return "true" == testLabSetting
        }

    private val analyticsPrefs: SharedPreferences by lazy {
        getSharedPreferences("analytics-prefs", Context.MODE_PRIVATE)
    }

    var isAnalyticsAllowed: Boolean
        get() = analyticsPrefs.getBoolean("allowed", true)
        set(value) {
            analyticsPrefs.edit {
                putBoolean("allowed", value)
            }

            // Change the flag with the providers
            analytics.setEnabled(value && !isInTestLab) // Never do analytics in the test lab
        }

    override fun onCreate() {
        super.onCreate()

        val googleAnalytics = com.geeksville.mesh.analytics.GoogleAnalytics(this)
        analytics = googleAnalytics

        // Set analytics per prefs
        isAnalyticsAllowed = isAnalyticsAllowed

        registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }
}
