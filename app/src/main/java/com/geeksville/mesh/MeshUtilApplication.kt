package com.geeksville.mesh

import android.os.Debug
import com.geeksville.android.AppPrefs
import com.geeksville.android.BuildUtils.isEmulator
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.util.Exceptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mapbox.mapboxsdk.Mapbox


class MeshUtilApplication : GeeksvilleApplication() {

    override fun onCreate() {
        super.onCreate()

        Logging.showLogs = BuildConfig.DEBUG

        // We default to off in the manifest - we turn on here if the user approves
        // leave off when running in the debugger
        if (!isEmulator && (!BuildConfig.DEBUG || !Debug.isDebuggerConnected())) {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(isAnalyticsAllowed)
            crashlytics.setCustomKey("debug_build", BuildConfig.DEBUG)

            val pref = AppPrefs(this)
            crashlytics.setUserId(pref.getInstallId()) // be able to group all bugs per anonymous user

            // Attach to our exception wrapper
            Exceptions.reporter = { exception, _, _ ->
                crashlytics.recordException(exception)
                crashlytics.sendUnsentReports()
            }

            if (isAnalyticsAllowed) {
                val standardLogger = Logging.printlog

                // send all log messages through crashyltics, so if we do crash we'll have those in the report
                Logging.printlog = { level, tag, message ->
                    crashlytics.log("$tag: $message")
                    standardLogger(level, tag, message)
                }
            }
        }

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
    }
}