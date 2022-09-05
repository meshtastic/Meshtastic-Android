package com.geeksville.mesh.concurrent

import com.geeksville.mesh.android.Logging


/**
 * Sometimes when starting services we face situations where messages come in that require computation
 * but we can't do that computation yet because we are still waiting for some long running init to
 * complete.
 *
 * This class lets you queue up closures to run at a later date and later on you can call run() to
 * run all the previously queued work.
 */
class DeferredExecution : Logging {
    private val queue = mutableListOf<() -> Unit>()

    /// Queue some new work
    fun add(fn: () -> Unit) {
        queue.add(fn)
    }

    /// run all work in the queue and clear it to be ready to accept new work
    fun run() {
        debug("Running deferred execution numjobs=${queue.size}")
        queue.forEach {
            it()
        }
        queue.clear()
    }
}