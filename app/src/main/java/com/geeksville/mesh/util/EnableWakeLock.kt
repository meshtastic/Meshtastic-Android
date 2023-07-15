package com.geeksville.mesh.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.geeksville.mesh.android.BuildUtils.errormsg

@SuppressLint("WakelockTimeout")
private fun PowerManager.WakeLock.safeAcquire() {
    if (!isHeld) try {
        acquire()
    } catch (e: SecurityException) {
        errormsg("WakeLock permission exception: ${e.message}")
    } catch (e: IllegalStateException) {
        errormsg("WakeLock acquire() exception: ${e.message}")
    }
}

private fun PowerManager.WakeLock.safeRelease() {
    if (isHeld) try {
        release()
    } catch (e: IllegalStateException) {
        errormsg("WakeLock release() exception: ${e.message}")
    }
}

@SuppressLint("InvalidWakeLockTag")
@Composable
fun EnableWakeLock(context: Context) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "ScreenLock")

        wakeLock.safeAcquire()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> wakeLock.safeRelease()
                Lifecycle.Event.ON_RESUME -> wakeLock.safeAcquire()
                else -> {}
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            wakeLock.safeRelease()
        }
    }
}
