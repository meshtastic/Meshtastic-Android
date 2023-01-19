package com.geeksville.mesh

import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeshUtilApplication : GeeksvilleApplication() {

    override fun onCreate() {
        super.onCreate()

        Logging.showLogs = BuildConfig.DEBUG

    }
}