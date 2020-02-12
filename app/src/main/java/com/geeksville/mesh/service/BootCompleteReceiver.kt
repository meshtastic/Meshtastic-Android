package com.geeksville.mesh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geeksville.android.Logging


class BootCompleteReceiver : BroadcastReceiver(), Logging {
    override fun onReceive(mContext: Context, intent: Intent) {
        // FIXME - start listening for bluetooth messages from our device
        info("Received boot complete announcement, starting mesh service")
        MeshService.startService(mContext)
    }
}