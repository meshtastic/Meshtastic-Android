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

    // Are we running on the emulator?
    val isEmulator
        get() = Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.FINGERPRINT.contains("emulator") ||
                setOf(Build.MODEL, Build.PRODUCT).contains("google_sdk") ||
                Build.MODEL.contains("sdk_gphone64") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
}
