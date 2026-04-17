/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.prefs

import kotlinx.atomicfu.AtomicRef
import kotlinx.collections.immutable.PersistentMap

/**
 * Look up [key] in [cache]; if absent, construct a value via [build] and insert it atomically.
 *
 * [build] is wrapped in a [Lazy] before being published to [cache], so concurrent first-access of the same key never
 * invokes [build] more than once — only the winner of the CAS has its [Lazy] evaluated, and all readers share that same
 * result. This matters when [build] eagerly launches a coroutine (e.g. `Flow.stateIn(scope, Eagerly, …)`): the naive
 * approach would leak the losing coroutine into a never-cancelled scope.
 */
@Suppress("ReturnCount")
internal inline fun <K, V> cachedFlow(
    cache: AtomicRef<PersistentMap<K, Lazy<V>>>,
    key: K,
    crossinline build: () -> V,
): V {
    cache.value[key]?.let {
        return it.value
    }
    val newLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { build() }
    while (true) {
        val current = cache.value
        current[key]?.let {
            return it.value
        }
        if (cache.compareAndSet(current, current.put(key, newLazy))) {
            return newLazy.value
        }
    }
}
