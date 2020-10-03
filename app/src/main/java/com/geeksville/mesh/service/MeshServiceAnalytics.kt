package com.geeksville.mesh.service

import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MyNodeInfo

class MeshServiceAnalytics {
    fun trackConnection(myNodeInfo: MyNodeInfo?, nodeCount: Int, nodeOnlineCount: Int) {
        val radioModel = DataPair("radio_model", myNodeInfo?.model ?: "unknown")

        GeeksvilleApplication.analytics.track(
            "mesh_connect",
            DataPair("num_nodes", nodeCount),
            DataPair("num_online", nodeOnlineCount),
            radioModel
        )

        // Once someone connects to hardware start tracking the approximate number of nodes in their mesh
        // this allows us to collect stats on what typical mesh size is and to tell difference between users who just
        // downloaded the app, vs has connected it to some hardware.
        GeeksvilleApplication.analytics.setUserInfo(
            DataPair("num_nodes", nodeCount),
            radioModel
        )
    }

    fun trackConnectionChanged(connectedTimeMs: Long) {
        val now = System.currentTimeMillis()
        GeeksvilleApplication.analytics.track(
            "connected_seconds",
            DataPair((now - connectedTimeMs) / 1000.0)
        )
    }

    fun trackDisconnect(nodeCount: Int, nodeOnlineCount: Int) {
        GeeksvilleApplication.analytics.track(
            "mesh_disconnect",
            DataPair("num_nodes", nodeCount),
            DataPair("num_online", nodeOnlineCount)
        )
        GeeksvilleApplication.analytics.track("num_nodes", DataPair(nodeCount))

    }

    fun trackUserInfo(myNodeInfo: MyNodeInfo, protoMyInfo: MeshProtos.MyNodeInfo) {
        GeeksvilleApplication.analytics.setUserInfo(
            DataPair("region", myNodeInfo.region),
            DataPair("firmware", myNodeInfo.firmwareVersion),
            DataPair("has_gps", myNodeInfo.hasGPS),
            DataPair("hw_model", myNodeInfo.model),
            DataPair("dev_error_count", protoMyInfo.errorCount)

        )
        if (protoMyInfo.errorCode != 0) {
            GeeksvilleApplication.analytics.track(
                "dev_error",
                DataPair("code", protoMyInfo.errorCode),
                DataPair("address", protoMyInfo.errorAddress),
                // We also include this info, because it is required to correctly decode address from the map file
                DataPair("firmware", myNodeInfo.firmwareVersion),
                DataPair("hw_model", myNodeInfo.model),
                DataPair("region", myNodeInfo.region)
            )
        }
    }

    fun trackSend(packet: DataPacket) {
        GeeksvilleApplication.analytics.track(
            "data_send",
            DataPair("num_bytes", packet.bytes?.size ?: 0),
            DataPair("type", packet.dataType)
        )

        GeeksvilleApplication.analytics.track(
            "num_data_sent",
            DataPair(1)
        )
    }

    fun trackLocationRequestsStarted() {
        GeeksvilleApplication.analytics.track("location_start")
    }

    fun trackLocationRequestStopped() {
        GeeksvilleApplication.analytics.track("location_stop")
    }

    fun trackDataReceived(bytesReceived: Int, typeId: Int) {
        GeeksvilleApplication.analytics.track(
            "num_data_receive",
            DataPair(1)
        )

        GeeksvilleApplication.analytics.track(
            "data_receive",
            DataPair("num_bytes", bytesReceived),
            DataPair("type", typeId)
        )
    }
}
