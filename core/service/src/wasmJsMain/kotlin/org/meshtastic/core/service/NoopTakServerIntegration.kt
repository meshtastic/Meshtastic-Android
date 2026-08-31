/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.TakServerIntegration

// Auto-discovered by CoreServiceModule's existing @ComponentScan("org.meshtastic.core.service") in this target's
// compilation — no separate wasmJs Koin module needed (same "no per-target duplicate scan" reasoning as
// core:database's SingleDatabaseProvider).
//
// core:takserver's TAK server is a raw TLS listener (SSLServerSocket) — a browser sandbox can never accept inbound
// connections, so ATAK/TAK integration is a permanent web impossibility, not a pending feature. This is a real,
// honest no-op, not a stand-in for future work.
@Single(binds = [TakServerIntegration::class])
internal class NoopTakServerIntegration : TakServerIntegration {
    override val isRunning: StateFlow<Boolean> = MutableStateFlow(false)

    override fun start(scope: CoroutineScope) = Unit

    override fun stop() = Unit
}
