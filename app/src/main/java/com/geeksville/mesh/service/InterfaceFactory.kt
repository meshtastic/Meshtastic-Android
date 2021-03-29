package com.geeksville.mesh.service

import android.content.Context

/**
 * A base class for the singleton factories that make interfaces.  One instance per interface type
 */
abstract class InterfaceFactory(val prefix: Char) {
    companion object {
        private val factories = mutableMapOf<Char, InterfaceFactory>()

        fun getFactory(l: Char) = factories.get(l)
    }

    protected fun registerFactory() {
        factories[prefix] = this
    }

    abstract fun createInterface(service: RadioInterfaceService, rest: String): IRadioInterface

    /** Return true if this address is still acceptable. For BLE that means, still bonded */
    open fun addressValid(context: Context, rest: String): Boolean = true
}