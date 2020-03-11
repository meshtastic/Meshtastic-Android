package com.geeksville.mesh

import android.os.Debug
import com.geeksville.android.BuildUtils.isEmulator
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.util.Exceptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mapbox.mapboxsdk.Mapbox


class MeshUtilApplication : GeeksvilleApplication(null, "58e72ccc361883ea502510baa46580e3") {

    override fun onCreate() {
        super.onCreate()

        Logging.showLogs = BuildConfig.DEBUG

        // We default to off in the manifest, FIXME turn on only if user approves
        // leave off when running in the debugger
        if (!isEmulator && (!BuildConfig.DEBUG || !Debug.isDebuggerConnected())) {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)

            // Attach to our exception wrapper
            Exceptions.reporter = { exception, _, _ ->
                crashlytics.recordException(exception)
            }
        }

        // Mapbox Access token
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
    }
}