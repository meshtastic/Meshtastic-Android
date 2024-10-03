package com.geeksville.mesh.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.MyNodeInfo

@Entity(tableName = "my_node")
data class MyNodeEntity(
    @PrimaryKey(autoGenerate = false)
    val myNodeNum: Int,
    val model: String?,
    val firmwareVersion: String?,
    val couldUpdate: Boolean, // this application contains a software load we _could_ install if you want
    val shouldUpdate: Boolean, // this device has old firmware
    val currentPacketId: Long,
    val messageTimeoutMsec: Int,
    val minAppVersion: Int,
    val maxChannels: Int,
    val hasWifi: Boolean,
) {
    /** A human readable description of the software/hardware version */
    val firmwareString: String get() = "$model $firmwareVersion"

    fun toMyNodeInfo() = MyNodeInfo(
        myNodeNum = myNodeNum,
        hasGPS = false,
        model = model,
        firmwareVersion = firmwareVersion,
        couldUpdate = couldUpdate,
        shouldUpdate = shouldUpdate,
        currentPacketId = currentPacketId,
        messageTimeoutMsec = messageTimeoutMsec,
        minAppVersion = minAppVersion,
        maxChannels = maxChannels,
        hasWifi = hasWifi,
        channelUtilization = 0f,
        airUtilTx = 0f,
    )
}
