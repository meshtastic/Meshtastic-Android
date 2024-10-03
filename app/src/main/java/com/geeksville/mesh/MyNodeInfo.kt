package com.geeksville.mesh

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// MyNodeInfo sent via special protobuf from radio
@Parcelize
data class MyNodeInfo(
    val myNodeNum: Int,
    val hasGPS: Boolean,
    val model: String?,
    val firmwareVersion: String?,
    val couldUpdate: Boolean, // this application contains a software load we _could_ install if you want
    val shouldUpdate: Boolean, // this device has old firmware
    val currentPacketId: Long,
    val messageTimeoutMsec: Int,
    val minAppVersion: Int,
    val maxChannels: Int,
    val hasWifi: Boolean,
    val channelUtilization: Float,
    val airUtilTx: Float
) : Parcelable {
    /** A human readable description of the software/hardware version */
    val firmwareString: String get() = "$model $firmwareVersion"
}
