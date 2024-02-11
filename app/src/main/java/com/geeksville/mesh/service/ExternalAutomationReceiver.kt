package com.geeksville.mesh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.util.exceptionReporter

class ExternalAutomationReceiver : BroadcastReceiver(), Logging {

    override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
        if (intent.action == MeshService.ACTION_CHANGE_DEVICE) {
            val address = intent.getStringExtra(EXTRA_ADDRESS) ?: "n"
            val serviceIntent = MeshService.createIntent().let {
                it.action = intent.action
                it.putExtra(EXTRA_ADDRESS, address)
            }
            MeshService.startService(context, serviceIntent)
        }
    }

}
