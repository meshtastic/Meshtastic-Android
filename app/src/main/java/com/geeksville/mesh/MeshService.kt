package com.geeksville.mesh

import android.app.Service
import android.content.Intent
import android.os.IBinder

class MeshService : Service() {

    override fun onBind(intent: Intent): IBinder {
        // Return the interface
        return binder
    }

    private val binder = object : IMeshService.Stub() {
        override fun setOwner(myId: String?, longName: String?, shortName: String?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun sendOpaque(destId: String?, payload: ByteArray?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getOnline(ids: Array<out String>?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun isConnected(): Boolean {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}