package com.geeksville.mesh

import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Wrapper around `Dispatchers` to allow for easier testing when using dispatchers
 * in injected classes.
 */
class CoroutineDispatchers @Inject constructor() {
    val main = Dispatchers.Main
    val mainImmediate = Dispatchers.Main.immediate
    val default = Dispatchers.Default
    val io = Dispatchers.IO
}