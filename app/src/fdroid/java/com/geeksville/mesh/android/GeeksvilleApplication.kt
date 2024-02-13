package com.geeksville.mesh.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.geeksville.mesh.analytics.AnalyticsProvider

open class GeeksvilleApplication : Application(), Logging {

    companion object {
        lateinit var analytics: AnalyticsProvider
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

    @Suppress("UNUSED_PARAMETER")
    fun askToRate(activity: AppCompatActivity) {
        // do nothing
    }

    override fun onCreate() {
        super.onCreate()

        val nopAnalytics = com.geeksville.mesh.analytics.NopAnalytics(this)
        analytics = nopAnalytics
        isAnalyticsAllowed = false
    }
}

fun Context.isGooglePlayAvailable(): Boolean = false