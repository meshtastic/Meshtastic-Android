package com.geeksville.mesh.service

import com.geeksville.android.Logging

class NopInterface : IRadioInterface {
    companion object : Logging, InterfaceFactory('n') {
        override fun createInterface(
            service: RadioInterfaceService,
            rest: String
        ): IRadioInterface = NopInterface()

        init {
            registerFactory()
        }
    }

    override fun handleSendToRadio(p: ByteArray) {
    }

    override fun close() {
    }

}