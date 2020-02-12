package com.geeksville.mesh

import android.os.Debug
import com.geeksville.android.GeeksvilleApplication
import com.google.firebase.crashlytics.FirebaseCrashlytics


class MeshUtilApplication : GeeksvilleApplication(null, "58e72ccc361883ea502510baa46580e3") {

    override fun onCreate() {
        super.onCreate()

        // We default to off in the manifest, FIXME turn on only if user approves
        // leave off when running in the debugger
        if (false && !Debug.isDebuggerConnected())
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

    }
}