package com.geeksville.mesh.service

import com.geeksville.android.Logging
import com.geeksville.mesh.repository.usb.UsbRepository

class NopInterface : IRadioInterface {
    companion object : Logging, InterfaceFactory('n') {
        override fun createInterface(
            service: RadioInterfaceService,
            usbRepository: UsbRepository, // Temporary until dependency injection transition is completed
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