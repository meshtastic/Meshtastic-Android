package com.geeksville.mesh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(mContext: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // FIXME - start listening for bluetooth messages from our device
        }
    }
}