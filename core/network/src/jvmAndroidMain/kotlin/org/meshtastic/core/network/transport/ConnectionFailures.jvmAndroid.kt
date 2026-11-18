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
package org.meshtastic.core.network.transport

import java.nio.channels.UnresolvedAddressException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * On JVM/Android nearly every socket failure already extends `java.io.IOException`, which the common predicate covers —
 * `ConnectException`, `SocketException`, `UnknownHostException`, `EOFException` and friends all qualify there.
 *
 * [UnresolvedAddressException] is the exception: Ktor throws it when a hostname cannot be resolved, and it extends
 * `IllegalArgumentException`, so it slips past every `catch (IOException)` in the transport stack and lands in the
 * generic handler that reports errors.
 */
internal actual fun Throwable.isPlatformConnectionFailure(): Boolean = this is UnresolvedAddressException

/** Ktor's CIO TLS throws `SSLException` subclasses; the platform trust manager throws `CertificateException`. */
internal actual fun Throwable.isTlsFailure(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is SSLException || it is CertificateException }

private const val MAX_CAUSE_DEPTH = 8
