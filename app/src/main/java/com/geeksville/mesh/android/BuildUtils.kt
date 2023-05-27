package com.geeksville.mesh.android

import android.os.Build

/**
 * Created by kevinh on 1/14/16.
 */
object BuildUtils : Logging {

    fun is64Bit(): Boolean {
        if (Build.VERSION.SDK_INT < 21)
            return false
        else
            return Build.SUPPORTED_64_BIT_ABIS.size > 0
    }

    fun isBuggyMoto(): Boolean {
        debug("Device type is: ${Build.DEVICE}")
        return Build.DEVICE == "osprey_u2" // Moto G
    }

    /// Are we running on the emulator?
    val isEmulator get() = Build.MODEL == "Android SDK built for x86" || Build.MODEL == "sdk_gphone_x86" || Build.MODEL == "sdk_gphone64_arm64" || Build.MODEL == "Android SDK built for x86_64"
}