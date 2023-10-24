package com.geeksville.mesh.repository.radio

import javax.inject.Inject
import javax.inject.Provider

/**
 * Entry point for create radio backend instances given a specific address.
 *
 * This class is responsible for building and dissecting radio addresses based upon
 * their interface type and the "rest" of the address (which varies per implementation).
 */
class InterfaceFactory @Inject constructor(
    private val nopInterfaceFactory: NopInterfaceFactory,
    private val specMap: Map<InterfaceId, @JvmSuppressWildcards Provider<InterfaceSpec<*>>>
)  {
    internal val nopInterface by lazy {
        nopInterfaceFactory.create("")
    }

    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String {
        return "${interfaceId.id}$rest"
    }

    fun createInterface(address: String): IRadioInterface {
        val (spec, rest) = splitAddress(address)
        return spec?.createInterface(rest) ?: nopInterface
    }

    fun addressValid(address: String?): Boolean {
        return address?.let {
            val (spec, rest) = splitAddress(it)
            spec?.addressValid(rest)
        } ?: false
    }

    private fun splitAddress(address: String): Pair<InterfaceSpec<*>?, String> {
        val c = address[0].let { InterfaceId.forIdChar(it) }?.let { specMap[it]?.get() }
        val rest = address.substring(1)
        return Pair(c, rest)
    }
}