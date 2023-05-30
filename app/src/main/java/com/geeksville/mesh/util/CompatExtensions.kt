package com.geeksville.mesh.util

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Parcel
import android.os.Parcelable
import androidx.core.content.IntentCompat
import androidx.core.os.ParcelCompat

object PendingIntentCompat {
    val FLAG_MUTABLE = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE
    } else {
        0
    }

    val FLAG_IMMUTABLE = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        PendingIntent.FLAG_IMMUTABLE
    } else {
        0
    }
}

object CompanionDeviceManagerCompat {
    @SuppressLint("InlinedApi")
    val EXTRA_DEVICE = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        CompanionDeviceManager.EXTRA_ASSOCIATION
    } else {
        @Suppress("DEPRECATION") CompanionDeviceManager.EXTRA_DEVICE
    }
}

inline fun <reified T : Parcelable> Parcel.readParcelableCompat(loader: ClassLoader?): T? =
    ParcelCompat.readParcelable(this, loader, T::class.java)

inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String?): T? =
    IntentCompat.getParcelableExtra(this, key, T::class.java)

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
    }
