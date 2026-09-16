/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.ios.runtime

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import platform.Foundation.NSProcessInfo
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/** Opt-in Debug console bridge for exact-stage device investigations; no generic log/payload forwarding. */
@OptIn(ExperimentalNativeApi::class)
internal fun installGatewayDiagnostics() {
    if (Platform.isDebugBinary && NSProcessInfo.processInfo.environment["MESHLINK_GATEWAY_DIAGNOSTICS"] == "1") {
        Logger.addLogWriter(GatewayDiagnosticWriter)
    }
}

private object GatewayDiagnosticWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (PREFIXES.any(message::startsWith)) println(message)
    }

    private val PREFIXES = listOf("gateway_outbox ", "gateway_ingress ", "gateway_dispatch ", "ios_ble_recovery ")
}
